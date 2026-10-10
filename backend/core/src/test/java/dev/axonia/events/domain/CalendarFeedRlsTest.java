package dev.axonia.events.domain;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.DedicatedPostgresContainer;
import dev.axonia.IsolationProbe;
import dev.axonia.events.adapter.out.persistence.CalendarSubscriptionEntity;
import dev.axonia.events.adapter.out.persistence.CalendarSubscriptionRepository;
import dev.axonia.shared.api.tenancy.OrganizationScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code calendar_subscription}'s own policies, through the REAL {@code axonia_app} role rather than the Testcontainers
 * owner, following the {@code RlsStackIsolationTest} recipe. An owner bypasses RLS by ownership, so every ordinary test
 * in the suite passes green over a policy that does not exist, and the failure would appear only in production - as the
 * database refusing to resolve any feed URL at all.
 *
 * <p>
 * The table's two policies pull in opposite directions and both halves are asserted here. Resolving a bearer to its row
 * necessarily happens before any organization is in scope, so {@code calendar_subscription_auth_lookup} permits that
 * one read with no {@code app.current_org} set; everything that writes stays behind {@code organization_isolation}, so
 * a connection standing in the wrong organization can neither stamp another tenant's subscription nor mint one in its
 * name.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Testcontainers
class CalendarFeedRlsTest {

    @Container
    static final DedicatedPostgresContainer PG = new DedicatedPostgresContainer();

    @DynamicPropertySource
    static void dataSourceProps(DynamicPropertyRegistry registry) {
        // Flyway migrates as the superuser - creates schema, axonia_app role, and RLS policies.
        registry.add("spring.flyway.url", PG::getJdbcUrl);
        registry.add("spring.flyway.user", PG::getUsername);
        registry.add("spring.flyway.password", PG::getPassword);

        // App datasource connects as the least-privilege role, subject to RLS.
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "axonia_app");
        registry.add("spring.datasource.password", () -> "axonia_app");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    private static final String OWNER_ORG = "rls-calendar-owner";
    private static final String OTHER_ORG = "rls-calendar-other";
    private static final String TOKEN = "rls-calendar-token";
    private static final String TOKEN_HASH = SubscriptionTokens.hash(TOKEN);

    private static final Instant SEEDED_AT = Instant.parse("2026-08-15T09:00:00Z");
    private static final Instant INTRUDERS_STAMP = Instant.parse("2026-08-15T10:00:00Z");
    private static final Instant OWNERS_STAMP = Instant.parse("2026-08-15T11:00:00Z");

    /**
     * Seeded through a superuser connection, which bypasses RLS by ownership, so the row genuinely exists and every
     * invisibility assertion below means something. The flag is what keeps a second test method from inserting it
     * again, and {@code @BeforeEach} is the earliest hook that reliably runs after Flyway has created the schema.
     */
    private static final AtomicBoolean SEEDED = new AtomicBoolean(false);

    @Autowired
    private CalendarSubscriptionRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void seedOnce() throws Exception {
        if (SEEDED.compareAndSet(false, true)) {
            try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                    var stmt = conn.createStatement()) {
                stmt.execute("""
                        INSERT INTO calendar_subscription
                            (organization_id, community_id, user_id, token_hash, created_at, version)
                        VALUES ('%s', 12, 'rls-calendar-user', '%s', TIMESTAMPTZ '%s', 0)
                        """.formatted(OWNER_ORG, TOKEN_HASH, SEEDED_AT));
            }
        }
    }

    @AfterEach
    void clearScope() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Not decoration, and the reason every assertion below means anything: the Testcontainers superuser owns this table
     * and ownership bypasses RLS outright, so a class wired to it would certify nothing about production.
     */
    @Test
    void theConnectionIsTheLeastPrivilegedRoleNotTheSchemaOwner() {

        assertThat(jdbc.queryForObject("SELECT current_user", Map.of(), String.class)).isEqualTo("axonia_app");
    }

    /**
     * The pre-scope read the whole feed hangs from. A calendar client carries no organization, so nothing has set
     * {@code app.current_org} by the time the token is resolved: without {@code calendar_subscription_auth_lookup} this
     * answers empty and every subscribed member's URL is a permanent 404.
     */
    @Test
    void theTokenLookupResolvesWithNoOrganizationInScope() {

        // given no authentication at all, so the connection is opened with no app.current_org
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        // when
        var actual = repository.findByTokenHash(TOKEN_HASH);

        // then
        assertThat(actual).isPresent().get()
                .satisfies(subscription -> assertThat(subscription.getOrganizationId()).isEqualTo(OWNER_ORG));
    }

    /**
     * The stamp is the one write a fetch makes, and it runs inside whichever organization the token resolved to. Scoped
     * to another one it must touch nothing: {@code organization_isolation} hides the row from the update, so the
     * statement reports success and changes not a column. The owner's own stamp lands immediately afterwards, so this
     * cannot pass for the trivial reason that stamping never works at all.
     */
    @Test
    void aStampFromAnotherOrganizationsScopeChangesNothingWhileTheOwnersLands() {

        // given
        var id = repository.findByTokenHash(TOKEN_HASH).orElseThrow().getId();

        // when
        OrganizationScope.runAs(OTHER_ORG, () -> repository.stampFetch(id, INTRUDERS_STAMP, "Intruder/1.0"));

        // then
        assertThat(storedStamp()).isNull();

        // and when the organization the subscription belongs to stamps it
        OrganizationScope.runAs(OWNER_ORG, () -> repository.stampFetch(id, OWNERS_STAMP, "Outlook/16.0"));

        // then
        assertThat(storedStamp()).isEqualTo(OWNERS_STAMP);
    }

    /**
     * The other direction, which no lost update could ever reveal: a connection standing in one organization must not
     * be able to mint a subscription belonging to another. {@code organization_isolation}'s WITH CHECK refuses the row
     * outright rather than filing it quietly under the wrong tenant.
     *
     * <p>
     * Asserted as a {@link DataAccessException} rather than as the concrete type Spring happens to hand back. Postgres
     * reports the refusal as SQLSTATE 42501, and the fallback translator files the whole {@code 42} class under
     * {@code BadSqlGrammarException} - a fact about the translator, not about the policy, and one a future translator
     * may well correct. The type says the database refused the write; the message says why.
     */
    @Test
    @IsolationProbe("calendar_subscription")
    void aSubscriptionCannotBeMintedInsideAnotherOrganizationsScope() {

        // given
        var forged = CalendarSubscriptionEntity.builder().organizationId(OWNER_ORG).communityId(12L)
                .userId("rls-calendar-intruder").tokenHash(SubscriptionTokens.hash("rls-calendar-forged")).build();

        // when / then
        assertThatThrownBy(() -> OrganizationScope.runAs(OTHER_ORG, () -> {
            repository.save(forged);
        })).isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
    }

    /** Read back through the superuser, which sees the row whatever the policies say, so the answer is the truth. */
    private static Instant storedStamp() {

        try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var stmt = conn.createStatement();
                var rows = stmt.executeQuery(
                        "SELECT last_fetched_at FROM calendar_subscription WHERE token_hash = '" + TOKEN_HASH + "'")) {
            rows.next();
            var at = rows.getTimestamp(1);
            return at == null ? null : at.toInstant();
        } catch (Exception e) {
            throw new IllegalStateException("could not read the seeded subscription back", e);
        }
    }
}
