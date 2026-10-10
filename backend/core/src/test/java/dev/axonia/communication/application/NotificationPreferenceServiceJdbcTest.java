package dev.axonia.communication.application;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.communication.adapter.out.persistence.CommunicationJdbcTestSupport;
import dev.axonia.communication.adapter.out.persistence.JdbcNotificationPreferenceStore;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.spi.MessageSubjectPolicy;
import dev.axonia.communication.application.port.out.CurrentUser;
import dev.axonia.communication.application.port.out.OrganizationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for {@link NotificationPreferenceService}'s upsert, run against real Postgres rather than mocks.
 * {@code NotificationPreferenceServiceTest} and {@code JdbcNotificationPreferenceStoreTest} already cover the behaviour
 * with a mocked store and a mocked {@code NamedParameterJdbcTemplate}; what that leaves unprotected is the SQL itself -
 * a wrong {@code ON CONFLICT} target, a column that does not match {@code communication_preference}'s actual shape, or
 * an {@code excluded} mistaken for the existing row - none of which a mock can catch, and all of which this class runs
 * against real Postgres exactly as {@code JdbcOutboxTest} and
 * {@code expertise.adapter.out.persistence.PreferenceRepositoryTest} do for the comparable writers this one's javadoc
 * cites as its idiom.
 *
 * <p>
 * Runs as the Testcontainers superuser (table owner), which bypasses {@code communication_preference}'s row-level
 * security by ownership - the same convention every other {@code JdbcTestSupport} integration test in this codebase
 * follows; RLS itself is {@code CommunicationRlsIsolationTest}'s job, not this class's. The organization and the
 * current user are doubles: who is signed in, and where, is the token adapters' concern, not this service's.
 */
@Import({NotificationPreferenceService.class, JdbcNotificationPreferenceStore.class, MessageSubjectPolicies.class,
        NotificationPreferenceServiceJdbcTest.OpenMessageTypes.class})
class NotificationPreferenceServiceJdbcTest extends CommunicationJdbcTestSupport {

    @TestConfiguration
    static class OpenMessageTypes {

        @Bean
        MessageSubjectPolicy openMessageTypes() {

            var policy = mock(MessageSubjectPolicy.class);
            when(policy.handles()).thenReturn(Set.copyOf(MessageType.open()));
            return policy;
        }
    }

    @Autowired
    private NotificationPreferenceService service;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @MockitoBean
    private OrganizationContext organizationContext;
    @MockitoBean
    private CurrentUser currentUser;

    @BeforeEach
    void signInAliceAtAcme() {

        when(organizationContext.organizationId()).thenReturn("acme");
        when(currentUser.userId()).thenReturn("alice-sub");
    }

    /**
     * The property {@code NotificationPreferenceService}'s own javadoc claims for the upsert: a second write to the
     * same {@code (organization, user, type)} row updates it in place rather than duplicating it, and does so through
     * exactly the columns {@code communication_preference} (migration {@code V27}) actually has. A mock can assert that
     * {@code jdbc.update} was called with the right parameters; only a real database can prove the SQL those parameters
     * are bound into actually behaves this way.
     */
    @Test
    void settingAPreferenceTwiceUpdatesTheSameRowRatherThanInserting() {

        service.set(MessageType.SESSION_WRAP_UP, false);
        var afterFirst = reload();

        service.set(MessageType.SESSION_WRAP_UP, true);
        var afterSecond = reload();

        assertThat(countRows()).isEqualTo(1);
        assertThat(afterSecond.enabled()).isTrue();
        assertThat(afterSecond.createdAt()).isEqualTo(afterFirst.createdAt());
        assertThat(afterSecond.updatedAt()).isAfter(afterFirst.updatedAt());
        assertThat(afterFirst.version()).isZero();
        assertThat(afterSecond.version()).isEqualTo(1L);
    }

    @Test
    void aFirstWriteInsertsExactlyOneRow() {

        service.set(MessageType.SESSION_UPCOMING, false);

        assertThat(countRows()).isEqualTo(1);
        assertThat(reload().enabled()).isFalse();
    }

    private long countRows() {

        var count = jdbc.queryForObject("SELECT count(*) FROM communication_preference", Map.of(), Long.class);
        return count == null ? 0 : count;
    }

    /**
     * Filters by organization and user only, not message type too: each test in this class writes exactly one
     * preference row, so {@code countRows() == 1} already establishes there is nothing else this query could match.
     */
    private PreferenceRow reload() {

        return jdbc.queryForObject(
                "SELECT enabled, created_at, updated_at, version FROM communication_preference "
                        + "WHERE organization_id = 'acme' AND user_id = 'alice-sub'",
                Map.of(),
                (rs, rowNum) -> new PreferenceRow(rs.getBoolean("enabled"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(), rs.getLong("version")));
    }

    private record PreferenceRow(boolean enabled, Instant createdAt, Instant updatedAt, long version) {
    }
}
