package dev.axonia.communication.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.application.port.out.CurrentUser;
import dev.axonia.communication.application.port.out.NotificationPreferenceStore;
import dev.axonia.communication.application.port.out.OrganizationContext;
import dev.axonia.communication.domain.NotificationPreference;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;

/**
 * The current user's notification preferences. An absent row means enabled: a member who never visits the settings page
 * has no rows at all, and every message type starts on.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceStore notificationPreferenceStore;
    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;
    private final MessageSubjectPolicies policies;

    @Transactional(readOnly = true)
    public List<NotificationPreference> preferences() {

        var organizationId = organizationContext.organizationId();
        var userId = currentUser.userId();
        Map<MessageType, Boolean> enabledByType = new LinkedHashMap<>();
        for (var type : policies.types()) {
            enabledByType.put(type, true);
        }
        for (var stored : notificationPreferenceStore.storedFor(organizationId, userId)) {
            MessageType.parse(stored.messageType()).filter(enabledByType::containsKey)
                    .ifPresent(type -> enabledByType.put(type, stored.enabled()));
        }
        return enabledByType.entrySet().stream().map(e -> new NotificationPreference(e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional
    public void set(MessageType type, boolean enabled) {

        if (!policies.declares(type)) {
            throw new ValidationException("UNKNOWN_MESSAGE_TYPE",
                    "Not a message type this installation sends: " + type.code());
        }
        notificationPreferenceStore.upsert(organizationContext.organizationId(), currentUser.userId(), type, enabled);
    }

    /**
     * The unsubscribe path's write. There is no JWT on that request, so no {@link CurrentUser} or
     * {@link OrganizationContext} to read - both are already known, verified, from the signed {@code UnsubscribeClaim}
     * instead. The caller MUST already be running inside {@code OrganizationScope.runAs(organizationId, ...)}: this
     * method only writes through the store's raw SQL, and it is that scope - entered before this {@code @Transactional}
     * method is, never from inside it - that sets {@code app.current_org} for RLS. {@code organizationId} is still
     * passed to the query explicitly regardless, the same defence-in-depth {@link NotificationPreferenceStore}
     * documents for every other caller. This is also the one caller of {@link NotificationPreferenceStore#upsert} that
     * cannot assume it is the only writer for its row - see that method's own javadoc.
     */
    @Transactional
    public void unsubscribe(String organizationId, String userId, MessageType type) {

        if (!policies.declares(type)) {
            return;
        }
        notificationPreferenceStore.upsert(organizationId, userId, type, false);
    }
}
