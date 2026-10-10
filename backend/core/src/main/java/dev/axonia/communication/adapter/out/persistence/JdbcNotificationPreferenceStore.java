package dev.axonia.communication.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.application.port.out.NotificationPreferenceStore;
import dev.axonia.communication.domain.StoredPreference;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcNotificationPreferenceStore implements NotificationPreferenceStore {

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

    @Override
    @Transactional(readOnly = true)
    public List<StoredPreference> storedFor(String organizationId, String userId) {

        return notificationPreferenceRepository.findByOrganizationIdAndUserId(organizationId, userId).stream()
                .map(JdbcNotificationPreferenceStore::toStoredPreference).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredPreference> storedFor(String organizationId, String userId, MessageType type) {

        return notificationPreferenceRepository
                .findByOrganizationIdAndUserIdAndMessageType(organizationId, userId, type.code())
                .map(JdbcNotificationPreferenceStore::toStoredPreference);
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
    @Override
    public void upsert(String organizationId, String userId, MessageType type, boolean enabled) {

        var now = Timestamp.from(clock.instant());
        var params = new MapSqlParameterSource().addValue("org", organizationId).addValue("userId", userId)
                .addValue("messageType", type.code()).addValue("enabled", enabled).addValue("now", now);
        jdbc.update(UPSERT_SQL, params);
    }

    static StoredPreference toStoredPreference(NotificationPreferenceEntity entity) {

        return new StoredPreference(entity.getMessageType(), entity.isEnabled());
    }
}
