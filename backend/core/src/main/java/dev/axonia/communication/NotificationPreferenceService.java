package dev.axonia.communication;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ValidationException;
import dev.axonia.communication.internal.MessageSubjectPolicies;
import dev.axonia.communication.internal.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;

/**
 * The current user's notification preferences. An absent row means enabled: a member who never visits the settings page
 * has no rows at all, and every message type starts on.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private static final String UPSERT_SQL = """
            INSERT INTO communication_preference
                (organization_id, user_id, message_type, enabled, created_at, updated_at, version)
            VALUES (:org, :userId, :messageType, :enabled, :now, :now, 0)
            ON CONFLICT (organization_id, user_id, message_type)
            DO UPDATE SET enabled = :enabled, updated_at = :now,
                          version = communication_preference.version + 1
            """;

    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
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
        for (var stored : notificationPreferenceRepository.findByOrganizationIdAndUserId(organizationId, userId)) {
            MessageType.parse(stored.getMessageType()).filter(enabledByType::containsKey)
                    .ifPresent(type -> enabledByType.put(type, stored.isEnabled()));
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
        upsert(organizationContext.organizationId(), currentUser.userId(), type, enabled);
    }

    /**
     * The unsubscribe path's write. There is no JWT on that request, so no {@link CurrentUser} or
     * {@link OrganizationContext} to read - both are already known, verified, from the signed {@code UnsubscribeClaim}
     * instead. The caller MUST already be running inside {@code OrganizationScope.runAs(organizationId, ...)}: this
     * method only writes through {@code jdbc}'s raw SQL, and it is that scope - entered before this
     * {@code @Transactional} method is, never from inside it - that sets {@code app.current_org} for RLS.
     * {@code organizationId} is still passed to the query explicitly regardless, the same defence-in-depth
     * {@link NotificationPreferenceRepository} documents for every other caller. This is also the one caller of
     * {@link #upsert} that cannot assume it is the only writer for its row - see that method's own javadoc.
     */
    @Transactional
    void unsubscribe(String organizationId, String userId, MessageType type) {

        if (!policies.declares(type)) {
            return;
        }
        upsert(organizationId, userId, type, false);
    }

    /**
     * A single {@code INSERT ... ON CONFLICT DO UPDATE}, not the read-then-write {@code find} followed by {@code save}
     * Spring Data JDBC's repository would otherwise offer: two callers racing the same
     * {@code (organizationId, userId, type)} row - a mail client's one-click {@code List-Unsubscribe} POST landing
     * within milliseconds of the person also clicking the footer link in the same message, say - would otherwise both
     * miss each other's row in their own {@code find}, then both attempt an {@code INSERT}, and the loser gets
     * {@code communication_preference_unique}'s {@code DuplicateKeyException} instead of the quiet no-op this write
     * must be. The unsubscribe path in particular cannot assume it is ever the only writer for its row.
     */
    private void upsert(String organizationId, String userId, MessageType type, boolean enabled) {

        var now = Timestamp.from(clock.instant());
        var params = new MapSqlParameterSource().addValue("org", organizationId).addValue("userId", userId)
                .addValue("messageType", type.code()).addValue("enabled", enabled).addValue("now", now);
        jdbc.update(UPSERT_SQL, params);
    }
}
