package dev.axonia;

import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.axonia.events.api.spi.ArtifactTrace;
import dev.axonia.events.api.spi.SessionTraceAccess;
import dev.axonia.recognition.api.ContributionLoveCounts;
import dev.axonia.recognition.api.ContributionType;
import dev.axonia.shared.api.tenancy.OrganizationScope;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two bulk counts behind a memory's traces, through the real {@code axonia_app} role rather than the Testcontainers
 * owner, which bypasses RLS by ownership: the owner-run slice tests prove the SQL predicates, this proves another
 * organization's loves and artifacts never count in production either, following {@code CalendarFeedRlsTest}.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Testcontainers
class MemoryTracesRlsTest {

    @Container
    static final DedicatedPostgresContainer PG = new DedicatedPostgresContainer();

    @DynamicPropertySource
    static void dataSourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", PG::getJdbcUrl);
        registry.add("spring.flyway.user", PG::getUsername);
        registry.add("spring.flyway.password", PG::getPassword);
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "axonia_app");
        registry.add("spring.datasource.password", () -> "axonia_app");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    private static final String OWN_ORG = "rls-traces-own";
    private static final String OTHER_ORG = "rls-traces-other";
    private static final long COMMUNITY_ID = 31L;
    private static final long SLOT_ID = 501L;
    private static final long SESSION_ID = 601L;

    /** Seeded once through the superuser, which bypasses RLS, so both organizations' rows genuinely exist. */
    private static final AtomicBoolean SEEDED = new AtomicBoolean(false);

    @Autowired
    private ContributionLoveCounts contributionLoveCounts;

    @Autowired
    private SessionTraceAccess sessionTraceAccess;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void seedOnce() throws Exception {
        if (SEEDED.compareAndSet(false, true)) {
            try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                    var stmt = conn.createStatement()) {
                seedLoves(stmt, OWN_ORG, 1);
                seedLoves(stmt, OTHER_ORG, 2);
                seedArtifact(stmt, OWN_ORG, 3);
                seedArtifact(stmt, OTHER_ORG, 5);
                seedArtifact(stmt, OTHER_ORG, 7);
            }
        }
    }

    @Test
    void theConnectionIsTheLeastPrivilegedRoleNotTheSchemaOwner() {

        assertThat(jdbc.queryForObject("SELECT current_user", Map.of(), String.class)).isEqualTo("axonia_app");
    }

    @Test
    void anOrganizationCountsOnlyItsOwnLovesAndArtifacts() {

        // when
        var loves = OrganizationScope.runAs(OWN_ORG,
                () -> contributionLoveCounts.countFor(OWN_ORG, ContributionType.SLOT_PRESENTED, List.of(SLOT_ID)));
        var traces = OrganizationScope.runAs(OWN_ORG,
                () -> sessionTraceAccess.artifactTraces(OWN_ORG, COMMUNITY_ID, List.of(SESSION_ID)));

        // then
        assertThat(loves).containsExactly(Map.entry(SLOT_ID, 1L));
        assertThat(traces).containsExactly(Map.entry(SESSION_ID, new ArtifactTrace(1, 3)));
    }

    /** Even a query naming the other organization outright reads nothing from inside another organization's scope. */
    @Test
    void anotherOrganizationsScopeCannotCountThem() {

        // when
        var loves = OrganizationScope.runAs(OTHER_ORG,
                () -> contributionLoveCounts.countFor(OWN_ORG, ContributionType.SLOT_PRESENTED, List.of(SLOT_ID)));
        var traces = OrganizationScope.runAs(OTHER_ORG,
                () -> sessionTraceAccess.artifactTraces(OWN_ORG, COMMUNITY_ID, List.of(SESSION_ID)));

        // then
        assertThat(loves).isEmpty();
        assertThat(traces).isEmpty();
    }

    private static void seedLoves(Statement stmt, String organizationId, int count) throws Exception {

        stmt.execute("""
                INSERT INTO recognized_contribution (organization_id, contribution_type, contribution_id, community_id,
                        title, occurred_at, created_at, updated_at, version)
                VALUES ('%s', 'SLOT_PRESENTED', %d, %d, 'Talk', now(), now(), now(), 0)
                """.formatted(organizationId, SLOT_ID, COMMUNITY_ID));
        for (var i = 0; i < count; i++) {
            stmt.execute("""
                    INSERT INTO contribution_love (organization_id, recognized_contribution_id, lover_user_id,
                            created_at, updated_at, version)
                    SELECT organization_id, id, 'lover-%d', now(), now(), 0 FROM recognized_contribution
                     WHERE organization_id = '%s' AND contribution_id = %d
                    """.formatted(i, organizationId, SLOT_ID));
        }
    }

    private static void seedArtifact(Statement stmt, String organizationId, int loves) throws Exception {

        stmt.execute("""
                INSERT INTO artifact (organization_id, community_id, type_id, title, description, url,
                        contributor_user_id, provenance_session_id, status, search_document, created_at, updated_at,
                        version, love_count)
                VALUES ('%s', %d, 1, 'T', 'd', 'https://example.com', 'author', %d, 'ACTIVE', 'doc', now(), now(), 0,
                        %d)
                """.formatted(organizationId, COMMUNITY_ID, SESSION_ID, loves));
    }
}
