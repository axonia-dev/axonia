package dev.axonia.installation.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import dev.axonia.communication.api.MailServerState;
import dev.axonia.identity.api.UserDirectory;
import dev.axonia.identity.api.UserDirectory.DirectoryUser;
import dev.axonia.identity.api.UserDirectory.MemberStatus;
import dev.axonia.installation.application.port.out.SettingsChangeLog;
import dev.axonia.installation.domain.MailField;
import dev.axonia.installation.domain.MailFieldChange;
import dev.axonia.installation.domain.MailServerOverlay;
import dev.axonia.installation.domain.MailServerRow;
import dev.axonia.installation.domain.SettingsChangeEntry;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class MailServerViews {

    private static final TypeReference<List<MailFieldChange>> CHANGES = new TypeReference<>() {
    };

    private final MailServerOverlay overlay;

    private final TestedFingerprints fingerprints;

    private final SettingsChangeLog history;

    private final UserDirectory directory;

    private final JsonMapper jsonMapper;

    public MailServerView view(ResolvedMailServer resolved, String organizationId) {

        var effective = resolved.effective();
        var row = resolved.row();
        var delivery = deliveryStateOf(resolved);
        var changed = changedSinceTest(row, delivery);
        var lastChange = history.latest().map(entry -> new MailLastChangeView(entry.occurredAt(),
                names(List.of(entry), organizationId).get(entry.actor()))).orElse(null);
        return MailServerView.builder().state(state(resolved, delivery, changed)).revision(row.revision())
                .host(field(MailField.HOST, effective.host())).port(field(MailField.PORT, effective.port()))
                .security(field(MailField.SECURITY, effective.security()))
                .username(field(MailField.USERNAME, effective.username()))
                .password(new MailServerPasswordView(effective.password() != null || resolved.passwordUnreadable(),
                        !resolved.passwordUnreadable(), overlay.lockedBy(MailField.PASSWORD)))
                .fromAddress(field(MailField.FROM_ADDRESS, effective.fromAddress()))
                .fromName(field(MailField.FROM_NAME, effective.fromName())).changedSinceTest(changed)
                .lastFailure(row.lastFailureReason() == null
                        ? null
                        : new MailFailureView(row.lastFailureReason(), row.lastFailureAt()))
                .lastChange(lastChange).build();
    }

    public Page<MailServerChangeView> changes(Pageable pageable, String organizationId) {

        var page = history.page(pageable);
        var names = names(page.getContent(), organizationId);
        return page.map(entry -> new MailServerChangeView(entry.id(), entry.occurredAt(), names.get(entry.actor()),
                jsonMapper.readValue(entry.changes(), CHANGES)));
    }

    /**
     * Settings delivery would refuse, which only a direct edit of the row stores, read as not configured, as delivery
     * holds them at the start: the card still learns the revision, so an Owner can clear or replace them.
     */
    private static MailServerState deliveryStateOf(ResolvedMailServer resolved) {

        try {
            return resolved.state();
        } catch (IllegalArgumentException e) {
            return MailServerState.notConfigured();
        }
    }

    private boolean changedSinceTest(MailServerRow row, MailServerState delivery) {

        var tested = row.testedFingerprint();
        return tested != null && delivery instanceof MailServerState.Configured configured
                && !fingerprints.matches(tested, configured.settings().fingerprint());
    }

    private static String state(ResolvedMailServer resolved, MailServerState delivery, boolean changedSinceTest) {

        if (resolved.passwordUnreadable()) {
            return "secret_unreadable";
        }
        if (delivery instanceof MailServerState.NotConfigured) {
            return "not_configured";
        }
        if (resolved.row().lastFailureReason() != null) {
            return "delivery_failing";
        }
        return changedSinceTest ? "changed_since_test" : "configured";
    }

    private <T> MailServerFieldView<T> field(MailField field, T value) {

        return new MailServerFieldView<>(value, overlay.lockedBy(field));
    }

    /**
     * Display names by subject, of members still in the Organization: an erased actor (null), one gone from the
     * directory, deactivated or erased there has none. A {@code HashMap}, so the name of a null actor reads as null.
     */
    private Map<String, String> names(Collection<SettingsChangeEntry> entries, String organizationId) {

        var actors = entries.stream().map(SettingsChangeEntry::actor).filter(Objects::nonNull).distinct().toList();
        var known = actors.isEmpty() ? List.<DirectoryUser>of() : directory.byIds(organizationId, actors);
        var names = new HashMap<String, String>();
        known.stream().filter(user -> user.status() == MemberStatus.PRESENT)
                .forEach(user -> names.put(user.userId(), user.displayName()));
        return names;
    }
}
