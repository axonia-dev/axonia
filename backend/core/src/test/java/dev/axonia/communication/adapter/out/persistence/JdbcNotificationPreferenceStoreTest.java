package dev.axonia.communication.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.domain.StoredPreference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcNotificationPreferenceStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Mock
    NotificationPreferenceRepository notificationPreferenceRepository;
    @Mock
    NamedParameterJdbcTemplate jdbc;

    JdbcNotificationPreferenceStore store;

    @BeforeEach
    void setUp() {
        store = new JdbcNotificationPreferenceStore(notificationPreferenceRepository, jdbc,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static boolean upsertParamsMatch(SqlParameterSource params, String organizationId, String userId,
            String messageType, boolean enabled) {
        var p = (MapSqlParameterSource) params;
        return organizationId.equals(p.getValue("org")) && userId.equals(p.getValue("userId"))
                && messageType.equals(p.getValue("messageType"))
                && Boolean.valueOf(enabled).equals(p.getValue("enabled"))
                && Timestamp.from(NOW).equals(p.getValue("now"));
    }

    @Test
    void storedPreferencesCarryEachRowsTypeCodeAndChoice() {

        when(notificationPreferenceRepository.findByOrganizationIdAndUserId("default", "user-1")).thenReturn(List.of(
                NotificationPreferenceEntityMother.any().messageType("session_wrap_up").enabled(false).build(),
                NotificationPreferenceEntityMother.any().messageType("retired_message_type").enabled(true).build()));

        assertThat(store.storedFor("default", "user-1")).containsExactly(new StoredPreference("session_wrap_up", false),
                new StoredPreference("retired_message_type", true));
    }

    @Test
    void oneTypesStoredPreferenceIsLookedUpByItsCode() {

        when(notificationPreferenceRepository.findByOrganizationIdAndUserIdAndMessageType("acme", "user-1",
                "session_wrap_up"))
                .thenReturn(Optional.of(NotificationPreferenceEntityMother.any().organizationId("acme")
                        .messageType("session_wrap_up").enabled(false).build()));

        assertThat(store.storedFor("acme", "user-1", MessageType.SESSION_WRAP_UP))
                .contains(new StoredPreference("session_wrap_up", false));
    }

    @Test
    void aTypeWithNoRowHasNoStoredPreference() {

        when(notificationPreferenceRepository.findByOrganizationIdAndUserIdAndMessageType("acme", "user-1",
                "session_upcoming")).thenReturn(Optional.empty());

        assertThat(store.storedFor("acme", "user-1", MessageType.SESSION_UPCOMING)).isEmpty();
    }

    @Test
    void anUpsertIsOneAtomicStatementStampedWithTheClock() {

        store.upsert("acme", "other-user", MessageType.SESSION_WRAP_UP, false);

        verify(jdbc).update(contains("ON CONFLICT (organization_id, user_id, message_type)"),
                argThat((SqlParameterSource params) -> upsertParamsMatch(params, "acme", "other-user",
                        "session_wrap_up", false)));
    }

    /**
     * Two callers racing the identical {@code (organization, user, type)} row must never surface as a
     * {@code communication_preference_unique} {@code DuplicateKeyException}. A read-then-write {@code find} followed by
     * {@code save} cannot promise that; one atomic {@code INSERT ... ON CONFLICT DO UPDATE} statement can, so this
     * asserts the write never reads the row first.
     */
    @Test
    void anUpsertNeverReadsTheRowFirst() {

        store.upsert("acme", "other-user", MessageType.SESSION_WRAP_UP, true);

        verifyNoInteractions(notificationPreferenceRepository);
        verify(jdbc).update(any(String.class), any(SqlParameterSource.class));
    }
}
