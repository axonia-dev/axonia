package dev.axonia.installation.application;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dev.axonia.communication.api.MailServerState;
import dev.axonia.installation.application.port.out.MailServerSettingsStore;
import dev.axonia.installation.domain.MailServerWatermark;
import dev.axonia.installation.domain.SecretField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Re-seals what an earlier key sealed under the current one: at each start, and at each revision check that reads the
 * row under another key this instance holds. During a rotation's rolling deploy, instances on either key may re-seal
 * the row back and forth; once only instances on the new key remain, it settles under it within one check. Each write
 * is conditional on the revision and the value read, so a re-seal never overwrites a save or another instance's
 * re-seal, and none moves the revision: the values are unchanged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingsKeyRotation {

    private final MailServerSettingsStore table;

    private final SettingsCipher cipher;

    private final TestedFingerprints fingerprints;

    private final MailServerResolver resolver;

    @EventListener(ApplicationReadyEvent.class)
    public void sealUnderTheCurrentKey() {

        reseal(true);
    }

    /**
     * From the key identifiers the check read with the watermark, so a row already under the current key costs nothing.
     * A row with something no held key can move is read again at every check: a flip-back by an instance still on the
     * other key must be undone at the next one.
     */
    public void sealUnderTheCurrentKey(MailServerWatermark seen) {

        if (cipher.holdsAsPrevious(seen.passwordKeyId()) || cipher.holdsAsPrevious(seen.fingerprintKeyId())) {
            reseal(false);
        }
    }

    /**
     * Says what it cannot move once, at the start, rather than at every check, and of a password only when the card
     * says it cannot be read: a password the deployment sets makes the stored one irrelevant.
     */
    private void reseal(boolean atStart) {

        var row = table.read();
        var sealed = row.passwordSecret();
        if (sealed != null && !cipher.sealedWithCurrentKey(sealed)) {
            cipher.resealed(SecretField.MAIL_PASSWORD, sealed).ifPresentOrElse(resealed -> {
                if (table.replacePasswordSecret(row.revision(), sealed, resealed)) {
                    log.info("Re-sealed the stored mail server password from settings key {} to settings key {}.",
                            keyIdOf(sealed), keyIdOf(resealed));
                }
            }, () -> {
                if (atStart && resolver.resolve().passwordUnreadable()) {
                    log.warn("The stored mail server password was sealed with a settings key this installation "
                            + "no longer holds; an Owner must enter it again.");
                }
            });
        }
        rekeyTestedFingerprint(atStart);
    }

    private void rekeyTestedFingerprint(boolean atStart) {

        var resolved = resolver.resolve();
        var tested = resolved.row().testedFingerprint();
        if (tested != null && stateOf(resolved, atStart) instanceof MailServerState.Configured configured) {
            fingerprints.rekeyed(tested, configured.settings().fingerprint())
                    .ifPresent(rekeyed -> table.replaceTestedFingerprint(resolved.row().revision(), tested, rekeyed));
        }
    }

    /**
     * Settings delivery would refuse leave the tested fingerprint under its key, and neither a start nor a check fails.
     */
    private static MailServerState stateOf(ResolvedMailServer resolved, boolean atStart) {

        try {
            return resolved.state();
        } catch (IllegalArgumentException e) {
            if (atStart) {
                log.warn("The tested fingerprint keeps its key: the stored mail server settings were refused ({}).",
                        e.getMessage());
            }
            return MailServerState.notConfigured();
        }
    }

    /** {@code v1:<key id>:<sealed>}; only an envelope a key just opened or sealed is passed here. */
    private static String keyIdOf(String envelope) {

        return envelope.split(":", 3)[1];
    }
}
