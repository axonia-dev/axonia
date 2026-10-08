package dev.axonia;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.axonia.identity.internal.UserEntity;
import dev.axonia.identity.internal.UserRepository;
import dev.axonia.roundup.RoundupSettingsService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end RLS isolation test through the REAL Spring stack.
 *
 * <p>
 * Unlike {@link RlsIsolationTest} (which exercises RLS via direct JDBC calls), this test boots the full Spring
 * application context with the app datasource connecting as {@code axonia_app} - the least-privilege runtime role that
 * is subject to Row-Level Security. It proves the complete chain:
 * <ol>
 * <li>A {@link org.springframework.security.oauth2.jwt.Jwt} in the {@link SecurityContextHolder} is read by
 * {@link OrganizationResolver}.</li>
 * <li>{@link dev.axonia.config.OrganizationAwareDataSource} picks up the resolved Organization and issues
 * {@code SET app.current_org = ?} before handing the connection to Spring Data JDBC.</li>
 * <li>Postgres RLS policies filter rows so cross-organization data is invisible.</li>
 * </ol>
 *
 * <p>
 * <strong>Flyway</strong> migrates as the container superuser (which bypasses RLS and creates the {@code axonia_app}
 * role). The <strong>app datasource</strong> is overridden to connect as {@code axonia_app} for this class only - the
 * normal suite keeps the {@code jdbc:tc} superuser connection intact.
 *
 * <p>
 * It covers {@code identity_user}, {@code roundup_settings}, and {@code artifact_opening}. A table belongs here when
 * its own slice test connects as the owner and therefore cannot prove its policy:
 * {@code RoundupSettingsServiceJdbcTest} says so about itself in as many words, and this is where the missing half of
 * that guarantee lives.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Testcontainers
class RlsStackIsolationTest {

    @Container
    static final DedicatedPostgresContainer PG = new DedicatedPostgresContainer();

    @DynamicPropertySource
    static void dataSourceProps(DynamicPropertyRegistry registry) {
        // Flyway migrates as the superuser - creates schema, axonia_app role, and RLS policies.
        registry.add("spring.flyway.url", PG::getJdbcUrl);
        registry.add("spring.flyway.user", PG::getUsername);
        registry.add("spring.flyway.password", PG::getPassword);

        // App datasource connects as the least-privilege role, subject to RLS.
        // Override the jdbc:tc scheme and TC driver set in application-DEV.properties.
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "axonia_app");
        registry.add("spring.datasource.password", () -> "axonia_app");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    /**
     * Seed flag: seeding uses DriverManager (bypasses RLS) and must happen after the Spring context is fully loaded
     * (and therefore after Flyway has created the schema). @BeforeEach is the earliest hook that reliably runs
     * post-context-load; the flag prevents repeated inserts across test methods.
     */
    private static final AtomicBoolean SEEDED = new AtomicBoolean(false);

    /** The two rows seeded below, one per tenant. Exactly one of them may ever be visible at a time. */
    private static final List<String> SEEDED_USER_IDS = List.of("stack-default", "stack-acme");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoundupSettingsService roundupSettings;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void seedOnce() throws Exception {
        if (SEEDED.compareAndSet(false, true)) {
            // Superuser connection bypasses RLS so both tenants' rows are inserted unconditionally.
            try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                    var stmt = conn.createStatement()) {
                stmt.execute(
                        """
                                INSERT INTO identity_user (organization_id, user_id, display_name, scim_id, arrival, created_at, updated_at, version)
                                VALUES ('default', 'stack-default', 'Stack Default User', 'scim-stack-default', 'SYNCED', now(), now(), 0)
                                """);
                stmt.execute(
                        """
                                INSERT INTO identity_user (organization_id, user_id, display_name, scim_id, arrival, created_at, updated_at, version)
                                VALUES ('acme', 'stack-acme', 'Stack Acme User', 'scim-stack-acme', 'SYNCED', now(), now(), 0)
                                """);
                stmt.execute("""
                        INSERT INTO roundup_settings (organization_id, enabled, created_at, updated_at, version)
                        VALUES ('default', true, now(), now(), 0), ('acme', true, now(), now(), 0)
                        """);
            }
        }
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Not decoration, and the reason every assertion below means anything: the Testcontainers superuser owns these
     * tables and ownership bypasses RLS outright, so a class wired to it would pass every isolation assertion here
     * while certifying nothing about production. This makes that wiring regressing fail loudly rather than silently.
     */
    @Test
    void theConnectionIsTheLeastPrivilegedRoleNotTheSchemaOwner() {

        assertThat(jdbc.queryForObject("SELECT current_user", Map.of(), String.class)).isEqualTo("axonia_app");
    }

    @Test
    void defaultTenantSeesOnlyItsOwnRows() {

        // given
        actAs("tenant-subject", "default");

        // then
        assertThat(visibleSeededUserIds()).containsExactly("stack-default");
    }

    @Test
    void acmeTenantSeesOnlyItsOwnRows() {

        // given
        actAs("acme-subject", "acme");

        // then
        assertThat(visibleSeededUserIds()).containsExactly("stack-acme");
    }

    /**
     * Deliberately an organization-unscoped read: {@code findAll} carries no organization predicate at all, so whatever
     * it hands back is exactly what Postgres RLS allowed the {@code axonia_app} role to see. Narrowed to the seeded ids
     * afterwards, in memory, so the assertion says nothing about any other row.
     */
    private List<String> visibleSeededUserIds() {

        return StreamSupport.stream(userRepository.findAll().spliterator(), false).map(UserEntity::getUserId)
                .filter(SEEDED_USER_IDS::contains).toList();
    }

    @Test
    void defaultTenantCannotSeeAcmeRowsViaDirectLookup() {

        // given
        actAs("tenant-subject", "default");

        // when
        var result = userRepository.findByOrganizationIdAndUserId("acme", "stack-acme");

        // then
        // RLS filters out the row even when the query names the acme Organization explicitly -
        // because the Postgres session GUC is 'default', the policy rejects the row.
        assertThat(result).isEmpty();
    }

    /**
     * {@code roundup_settings}' own policy, through the read the delivery gate and the producer both make. RLS coverage
     * has to run as {@code axonia_app} rather than as the Testcontainers owner, and
     * {@code RoundupSettingsServiceJdbcTest} says honestly that it cannot supply it: connected as the table owner, a
     * cross-tenant {@code enabledFor} would answer that organization's stored flag and prove nothing.
     *
     * <p>
     * Here it answers false, because the policy hides the row from a connection scoped to another organization - and
     * false is exactly the fail-closed answer the roundup needs, since a leaked true would send an entire tenant's
     * quarterly mail on a neighbour's setting. The same-organization read is asserted alongside it so this cannot pass
     * for the trivial reason that {@code enabledFor} always answers false.
     */
    @Test
    @IsolationProbe("roundup_settings")
    void aTenantsRoundupSettingIsInvisibleToAnyOtherTenant() {

        // given
        actAs("tenant-subject", "default");

        // then
        assertThat(roundupSettings.enabledFor("default")).isTrue();
        assertThat(roundupSettings.enabledFor("acme")).isFalse();
    }

    /**
     * {@code artifact_opening}'s own policy. No repository or service exists for this table yet - it is written
     * directly here, the same way {@link RlsIsolationTest} probes a table before its slice exists - so this is the only
     * place this policy is proven at all.
     */
    @Test
    @IsolationProbe("artifact_opening")
    void anOrganizationsArtifactOpeningIsInvisibleToAnyOtherOrganization() {

        // given - one open recorded under org-a
        actAs("org-a-subject", "org-a");
        long artifactId = jdbc.queryForObject("""
                INSERT INTO artifact (organization_id, community_id, type_id, title, description, url,
                        contributor_user_id, status, search_document, created_at, updated_at, version)
                VALUES ('org-a', 1, 1, 'T', 'd', 'https://example.com', 'author', 'ACTIVE', 'doc', now(), now(), 0)
                RETURNING id
                """, Map.of(), Long.class);
        jdbc.update(
                """
                        INSERT INTO artifact_opening (organization_id, community_id, artifact_id, member_user_id, opened_on, created_at)
                        VALUES ('org-a', 1, :artifactId, 'reader', CURRENT_DATE, now())
                        """,
                Map.of("artifactId", artifactId));

        // then - org-a sees its own opening ...
        assertThat(jdbc.queryForObject("SELECT count(*) FROM artifact_opening", Map.of(), Long.class)).isOne();

        // when - org-b looks
        actAs("org-b-subject", "org-b");

        // then - ... but org-b sees nothing
        assertThat(jdbc.queryForObject("SELECT count(*) FROM artifact_opening", Map.of(), Long.class)).isZero();
    }

    // ---- helpers ----

    private static void actAs(String subject, String organization) {
        var jwt = Jwt.withTokenValue("test-token-" + organization).header("alg", "none").subject(subject)
                .claim("org", organization).build();
        var auth = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
