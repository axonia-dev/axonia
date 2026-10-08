package dev.axonia.installation.internal;

import org.springframework.stereotype.Component;

import dev.axonia.communication.MailSecurity;
import dev.axonia.communication.MailServerSettings;
import lombok.RequiredArgsConstructor;

/**
 * The values a test sends with and a save writes: the deployment's where it locks a field, the request's elsewhere. A
 * password the request leaves out is the stored one, and a password the deployment sets is that one, each only for the
 * server it is held for: the same host, port and username, with no weaker security. A request with no login never reads
 * the stored password.
 */
@Component
@RequiredArgsConstructor
class MailServerCandidates {

    private final MailServerOverlay overlay;

    private final SettingsCipher cipher;

    MailServerSettings settings(MailValues requested, ResolvedMailServer resolved) {

        MailValueChecks.checkPassword(requested.password());
        var effective = effective(requested, resolved);
        MailValueChecks.checkFormats(effective);
        return effective.settings().orElseThrow(() -> MailValueChecks.missing(effective));
    }

    /** The row a tested save writes: a locked field keeps its stored value, and no login clears the password. */
    MailServerRow next(MailValues requested, ResolvedMailServer resolved, String testedFingerprint) {

        var before = resolved.row();
        var stored = requested.with(overlay::locks, before.values(null));
        return MailServerRow.builder().host(stored.host()).port(stored.port()).security(stored.security())
                .username(stored.username())
                .passwordSecret(passwordSecret(requested, effective(requested, resolved), before))
                .fromAddress(stored.fromAddress()).fromName(stored.fromName()).testedFingerprint(testedFingerprint)
                .revision(before.revision()).build();
    }

    private MailValues effective(MailValues requested, ResolvedMailServer resolved) {

        var draft = overlay.values().over(requested);
        if (draft.username() == null) {
            return draft;
        }
        var current = overlay.values().over(resolved.stored());
        if (overlay.locks(MailField.PASSWORD)) {
            if (!sameServer(draft, current)) {
                throw MailValueChecks.invalid(MailField.PASSWORD,
                        "the deployment sets this password for the current server.");
            }
            return draft;
        }
        if (requested.password() != null) {
            return draft;
        }
        var kept = kept(resolved);
        if (kept != null && !sameServer(draft, current)) {
            throw MailValueChecks.invalid(MailField.PASSWORD, "enter the password again for another server.");
        }
        return draft.toBuilder().password(kept).build();
    }

    /**
     * A draft missing a host, port or security is refused as missing before any password is sent, so it is not read as
     * another server here.
     */
    private static boolean sameServer(MailValues draft, MailValues current) {

        return incomplete(draft) || (draft.host().equals(current.host()) && draft.port().equals(current.port())
                && draft.username().equals(current.username()) && !weaker(draft.security(), current.security()));
    }

    private static boolean incomplete(MailValues draft) {

        return draft.host() == null || draft.port() == null || draft.security() == null;
    }

    private static boolean weaker(MailSecurity draft, MailSecurity current) {

        return draft == MailSecurity.NONE && current != MailSecurity.NONE;
    }

    private static String kept(ResolvedMailServer resolved) {

        if (resolved.passwordUnreadable()) {
            throw MailValueChecks.invalid(MailField.PASSWORD, "the stored password cannot be read; enter it again.");
        }
        return resolved.stored().password();
    }

    private String passwordSecret(MailValues requested, MailValues effective, MailServerRow before) {

        if (overlay.locks(MailField.PASSWORD)) {
            return before.passwordSecret();
        }
        if (effective.username() == null) {
            return null;
        }
        return requested.password() == null
                ? before.passwordSecret()
                : cipher.seal(SecretField.MAIL_PASSWORD, requested.password());
    }
}
