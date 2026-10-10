package dev.axonia.communication.application;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.DedicatedPostgresContainer;
import dev.axonia.IsolationProbe;
import dev.axonia.communication.adapter.in.schedule.CommunicationSweep;
import dev.axonia.communication.adapter.out.persistence.JdbcOutbox;
import dev.axonia.communication.adapter.out.persistence.JdbcProductionWatermark;
import dev.axonia.communication.adapter.out.persistence.NotificationPreferenceRepository;
import dev.axonia.communication.api.MessageOutcome;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.application.port.out.MailBatch;
import dev.axonia.communication.application.port.out.MailDelivery;
import dev.axonia.communication.application.port.out.Outbox;
import dev.axonia.identity.api.UserDirectory;
import dev.axonia.identity.api.UserDirectory.DirectoryUser;
import dev.axonia.identity.api.UserDirectory.MemberStatus;
import dev.axonia.organization.api.OrganizationIds;
import dev.axonia.shared.api.tenancy.OrganizationScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the sweep's own reads survive Row-Level Security, not merely a mocked stand-in for it: this is the one place
 * in the suite that calls {@link JdbcOutbox#claim}, {@link JdbcOutbox#outcomeFor} and reads
 * {@link NotificationPreferenceRepository} through the real {@code axonia_app} role rather than the shared
 * {@code jdbc:tc} container's superuser, which bypasses RLS by table ownership and would let a broken scope pass
 * silently.
 *
 * <p>
 * Follows the {@code RlsStackIsolationTest} recipe exactly, not {@code CommunicationJdbcTestSupport}'s shared
 * {@code @DataJdbcTest} base: Flyway migrates as the container superuser (creates the schema, the {@code axonia_app}
 * role, and the RLS policies), and the app datasource is overridden to connect as {@code axonia_app} for this class
 * only, against its own dedicated, throwaway Testcontainers Postgres. That last part is deliberate, not incidental -
 * see {@link SweepUnitScopeBoundaryTest}'s javadoc for why committing a claimable {@code communication_delivery} row
 * against the SHARED {@code jdbc:tc} container is a landmine: its superuser connection would claim the row
 * cross-organization the next time any test runs the real {@link OutboxDrain#drain}. Every row this class commits lives
 * and dies with its own container, so it can never reach that shared container at all.
 *
 * <p>
 * {@link #theConnectionIsTheLeastPrivilegedRoleNotTheSchemaOwner} is not decoration: a test connected as the owner
 * would pass both isolation assertions trivially, which is worse than no test at all, since it would certify production
 * working while actually proving nothing. This assertion is what makes that failure mode itself fail loudly, rather
 * than trusting the {@code @DynamicPropertySource} wiring below to never regress silently.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Testcontainers
class CommunicationRlsIsolationTest {

    @Container
    static final DedicatedPostgresContainer PG = new DedicatedPostgresContainer();

    /**
     * Only {@code spring.datasource.*} and {@code spring.flyway.*} are overridden here.
     * {@code axonia.tenant-discovery.datasource.*} ({@code TenantDiscoveryPool}, which backs {@link OrganizationIds})
     * and a second read pool keep whatever {@code application-DEV.properties} sets under the test profile - the shared
     * {@code jdbc:tc} container, not this class's own {@link #PG}. That is harmless for every test in this class, none
     * of which touches either pool, but it means this context boots pools against two different databases at once.
     * {@code RlsStackIsolationTest} has the identical shape, so this is precedent, not a regression - but it is a trap
     * for whoever adds to this class later: an assertion routed through {@link OrganizationIds} or a second-pool query
     * here would read answers from the wrong database entirely, silently, rather than from {@link #PG}.
     */
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

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    /**
     * Every seeded row uses an organization id built from this counter, unique per test method rather than the literal
     * {@code "org-1"}/{@code "org-2"}: this class is a non-transactional {@code @SpringBootTest} against its own
     * container, so nothing rolls back between methods, and JUnit does not guarantee method execution order. An
     * unscoped claim correctly leaves its seeded row untouched (that is the point being proved), and a preference read
     * never consumes what it sees at all - so a row genuinely from an EARLIER test's "org-1" is still there, and still
     * genuinely belongs to org-1, for a LATER test's scoped read to pick up alongside its own. Reusing the same
     * organization id across methods, first tried here, made exactly that happen:
     * {@code aScopedReadOfPreferencesSeesItsOwnOrganizationOnly} saw two rows instead of one, both correctly scoped to
     * "org-1", because an earlier method's row was still sitting there. A fresh id per test method removes the
     * possibility entirely, independent of run order.
     */
    private static final AtomicInteger SEQ = new AtomicInteger();

    /**
     * A separate counter from {@link #SEQ}: {@link #seedOutboxRow} used to advance {@code SEQ} itself to build a unique
     * {@code subject_ref}, which meant organization ids silently skipped numbers whenever an outbox row was seeded -
     * harmless, but it muddied the "one counter, one invariant" reading of {@code SEQ}'s own javadoc. This one exists
     * purely to keep {@code subject_ref} unique across every row this class ever inserts.
     */
    private static final AtomicInteger ROW_SEQ = new AtomicInteger();

    @Autowired
    private JdbcOutbox outbox;

    @Autowired
    private NotificationPreferenceRepository preferences;

    @Autowired
    private JdbcProductionWatermark watermark;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /**
     * The bean {@link #aScopedRowProcessorCallMarksItsOwnRowSent} and
     * {@link #anUnscopedRowProcessorCallLeavesTheRowUnmarked} exist to prove: the real one, wired against this class's
     * real {@code axonia_app}-connected {@link JdbcOutbox}, not a mock of {@code OutboxRowProcessor} itself, which
     * would prove nothing about whether {@code markSent}'s {@code UPDATE} actually lands under RLS.
     */
    @Autowired
    private OutboxRowProcessor rowProcessor;

    /**
     * The real bean, wired to the real {@link DrainWorkers} pool and this class's real {@code axonia_app}-connected
     * {@link JdbcOutbox} - see {@link #aDrainAcrossSeveralWorkersMarksEveryRowSentThroughTheRealRlsRole} for why a
     * mocked pool, or an owner-connected one, would prove nothing about the scope reaching the connection.
     */
    @Autowired
    private OutboxDrain drain;

    @MockitoBean
    private DeliveryGate gate;
    @MockitoBean
    private MailRenderer renderer;
    @MockitoBean
    private MailDelivery delivery;
    @MockitoBean
    private UserDirectory userDirectory;
    @MockitoBean
    private UnsubscribeTokens unsubscribeTokens;

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void theConnectionIsTheLeastPrivilegedRoleNotTheSchemaOwner() {

        assertThat(jdbc.queryForObject("SELECT current_user", Map.of(), String.class)).isEqualTo("axonia_app");
    }

    @Test
    void anUnscopedReadSeesNothingAtAll() throws SQLException {

        var org = uniqueOrg();
        seedOutboxRow(org, "user-1");
        // Positive control: proves the row genuinely landed, so an empty claim() below cannot be an empty table in
        // disguise - read back over the same superuser connection seeding used, not through axonia_app.
        assertThat(countOutboxRows(org)).isEqualTo(1);

        var claimed = outbox.claim(claimQuery());

        assertThat(claimed).isEmpty();
    }

    @Test
    void aScopedReadSeesItsOwnOrganizationOnly() throws SQLException {

        var orgOne = uniqueOrg();
        var orgTwo = uniqueOrg();
        seedOutboxRow(orgOne, "user-1");
        seedOutboxRow(orgTwo, "user-2");

        var claimed = OrganizationScope.runAs(orgOne, () -> outbox.claim(claimQuery()));

        assertThat(claimed).singleElement().satisfies(r -> assertThat(r.getOrganizationId()).isEqualTo(orgOne));
    }

    /**
     * {@code outcomeFor} is a {@code SELECT}, not the claim's {@code UPDATE}, but carries the identical
     * {@code organization_id = current_setting('app.current_org', true)} predicate - see {@link JdbcOutbox}'s class
     * javadoc - so an unscoped connection must fail closed to {@link MessageOutcome} with every count at zero, the same
     * way an unscoped {@code claim} sees no rows, rather than leaking another organization's counts.
     */
    @Test
    void anUnscopedOutcomeReadSeesAllZeros() throws SQLException {

        var org = uniqueOrg();
        var subjectRef = seedOutboxRow(org, "user-1");
        // Positive control, same reasoning as anUnscopedReadSeesNothingAtAll above.
        assertThat(countOutboxRows(org)).isEqualTo(1);

        var outcome = outbox.outcomeFor(MessageType.SESSION_WRAP_UP, subjectRef);

        assertThat(outcome).isEqualTo(MessageOutcome.builder().pending(0).sent(0).suppressed(0).abandoned(0).build());
    }

    @Test
    void aScopedOutcomeReadCountsItsOwnOrganizationOnly() throws SQLException {

        var orgOne = uniqueOrg();
        var orgTwo = uniqueOrg();
        var subjectRef = orgOne + "-shared-subject";
        seedOutboxRow(orgOne, subjectRef, "user-1");
        seedOutboxRow(orgTwo, subjectRef, "user-2");

        var outcome = OrganizationScope.runAs(orgOne, () -> outbox.outcomeFor(MessageType.SESSION_WRAP_UP, subjectRef));

        assertThat(outcome).isEqualTo(MessageOutcome.builder().pending(1).sent(0).suppressed(0).abandoned(0).build());
    }

    /**
     * The two tables V34 split the outbox into each carry their own {@code organization_isolation} policy, and neither
     * is reached by any of the port-level reads above: {@code claim} and {@code outcomeFor} both also carry an explicit
     * {@code current_setting} predicate of their own, so they would fail closed here even with RLS switched off
     * entirely. These two tests are the ones that exercise the policies themselves, straight through the
     * {@code axonia_app} pool with no application predicate in the way.
     */
    @Test
    void anUnscopedConnectionSeesNoMessagesOrDeliveries() throws SQLException {

        var org = uniqueOrg();
        seedOutboxRow(org, "user-1");
        // Positive control, same reasoning as anUnscopedReadSeesNothingAtAll above: read back over the superuser
        // connection seeding used, so an empty scoped read below cannot be an empty table in disguise.
        assertThat(countMessageRows(org)).isEqualTo(1);
        assertThat(countOutboxRows(org)).isEqualTo(1);

        assertThat(visibleMessageOrganizations(List.of(org))).isEmpty();
        assertThat(visibleDeliveryOrganizations(List.of(org))).isEmpty();
    }

    @Test
    @IsolationProbe("communication_message")
    @IsolationProbe("communication_delivery")
    void aScopedConnectionSeesOnlyItsOwnOrganizationsMessagesAndDeliveries() throws SQLException {

        var orgOne = uniqueOrg();
        var orgTwo = uniqueOrg();
        seedOutboxRow(orgOne, "user-1");
        seedOutboxRow(orgTwo, "user-2");
        var seeded = List.of(orgOne, orgTwo);

        assertThat(OrganizationScope.runAs(orgOne, () -> visibleMessageOrganizations(seeded))).containsExactly(orgOne);
        assertThat(OrganizationScope.runAs(orgOne, () -> visibleDeliveryOrganizations(seeded))).containsExactly(orgOne);
    }

    /**
     * Restricted to the organizations the calling method seeded rather than counting the whole table: nothing rolls
     * back in this class (see {@link #SEQ}), so an unrestricted read would also see every other method's rows and turn
     * the result into a function of run order instead of of RLS.
     */
    private List<String> visibleMessageOrganizations(List<String> seeded) {

        return jdbc.queryForList("SELECT organization_id FROM communication_message WHERE organization_id IN (:orgs)",
                new MapSqlParameterSource("orgs", seeded), String.class);
    }

    /** The delivery half of {@link #visibleMessageOrganizations}, restricted for the identical reason. */
    private List<String> visibleDeliveryOrganizations(List<String> seeded) {

        return jdbc.queryForList("SELECT organization_id FROM communication_delivery WHERE organization_id IN (:orgs)",
                new MapSqlParameterSource("orgs", seeded), String.class);
    }

    @Test
    void anUnscopedReadOfPreferencesSeesNothingAtAll() throws SQLException {

        var org = uniqueOrg();
        seedPreferenceRow(org, "user-1", MessageType.SESSION_WRAP_UP);
        // Positive control: proves the row genuinely landed, so an empty findAll() below cannot be an empty table in
        // disguise - read back over the same superuser connection seeding used, not through axonia_app.
        assertThat(countPreferenceRows(org)).isEqualTo(1);

        assertThat(preferences.findAll()).isEmpty();
    }

    @Test
    @IsolationProbe("communication_preference")
    void aScopedReadOfPreferencesSeesItsOwnOrganizationOnly() throws SQLException {

        var orgOne = uniqueOrg();
        var orgTwo = uniqueOrg();
        seedPreferenceRow(orgOne, "user-1", MessageType.SESSION_UPCOMING);
        seedPreferenceRow(orgTwo, "user-2", MessageType.SESSION_UPCOMING);

        var visible = OrganizationScope.runAs(orgOne, () -> preferences.findAll());

        assertThat(visible).singleElement().satisfies(r -> assertThat(r.getOrganizationId()).isEqualTo(orgOne));
    }

    /**
     * {@link JdbcProductionWatermark#since} carries the identical {@code current_setting} defence-in-depth predicate
     * every other read in this class does, so it fails closed to the fixture floor for an unscoped connection exactly
     * like {@link #anUnscopedReadOfPreferencesSeesNothingAtAll} above - included here for the same "real role, not a
     * mocked stand-in" reason, not because the mechanism differs.
     */
    @Test
    @IsolationProbe("communication_production_watermark")
    void aScopedSinceSeesItsOwnOrganizationOnly() throws SQLException {

        var orgOne = uniqueOrg();
        var orgTwo = uniqueOrg();
        seedWatermarkRow(orgOne, "session-wrap-up", NOW);
        seedWatermarkRow(orgTwo, "session-wrap-up", NOW.plus(Duration.ofDays(1)));

        var seenForOrgOne = OrganizationScope.runAs(orgOne, () -> watermark.since("session-wrap-up", Instant.EPOCH));
        var unscoped = watermark.since("session-wrap-up", Instant.EPOCH);

        assertThat(seenForOrgOne).isEqualTo(NOW);
        assertThat(unscoped).isEqualTo(Instant.EPOCH);
    }

    /**
     * {@link JdbcProductionWatermark#advanceTo} is the one write in this whole class that resolves its own
     * {@code organization_id} through {@link dev.axonia.communication.application.port.out.OrganizationContext} rather
     * than taking it as a parameter the caller already has - every other write here ({@code markSent},
     * {@code markFailed}, {@code
     * release}) is handed an {@code organizationId} up front and simply fails its {@code UPDATE} closed to zero rows
     * when unscoped, exactly like a read. {@code advanceTo} is different in kind, not degree: with no authentication in
     * the security context at all,
     * {@link dev.axonia.communication.application.port.out.OrganizationContext#organizationId()} itself has nothing to
     * resolve and throws before a single statement reaches the database - the row is never even attempted, so there is
     * no zero-row silent failure mode to fall back on here the way there is for every other write.
     */
    @Test
    void anUnscopedAdvanceToIsRejected() {

        assertThatThrownBy(() -> watermark.advanceTo("session-wrap-up", NOW)).isInstanceOf(RuntimeException.class);
    }

    /**
     * The point here: {@link OutboxRowProcessor#process} is a NEW transaction, with its own connection distinct from
     * whatever {@link JdbcOutbox#claim} used to fetch the row. Proving that connection also carries
     * {@code app.current_org} needs more than {@code SweepUnitScopeBoundaryTest}'s cheap wiring check over the shared
     * superuser container - see this class's own javadoc for why that container would pass the identical assertions
     * even if the GUC were never set anywhere. {@code markSent}'s {@code UPDATE} carries an explicit
     * {@code organization_id = :org} predicate (see {@link JdbcOutbox#markStatus}), matching this row's real
     * organization either way, so the ONLY thing that can make it actually touch zero rows here is
     * {@code organization_isolation}'s RLS policy hiding the row from an {@code axonia_app} connection that never had
     * {@code app.current_org} set on it. A status still {@code SENDING} after this call, not {@code SENT}, is exactly
     * what {@link #anUnscopedRowProcessorCallLeavesTheRowUnmarked} demonstrates for the connection that never got
     * scoped - proof this test is not vacuously green.
     */
    @Test
    void aScopedRowProcessorCallMarksItsOwnRowSent() throws SQLException {

        var org = uniqueOrg();
        seedOutboxRow(org, "user-1");
        var claimed = OrganizationScope.runAs(org, () -> outbox.claim(claimQuery())).getFirst();
        stubRowProcessorCollaboratorsFor(org, "user-1");

        OrganizationScope.runAs(org, () -> rowProcessor.process(claimed, mock(MailBatch.class)));

        assertThat(statusOf(claimed.getId())).isEqualTo("SENT");
    }

    /**
     * The negative half of {@link #aScopedRowProcessorCallMarksItsOwnRowSent}: the identical row, the identical
     * {@code axonia_app} connection, but {@link OutboxRowProcessor#process} called with no {@code OrganizationScope}
     * active at all. {@code markSent}'s {@code UPDATE} runs, is not rejected, and reports success - RLS makes it match
     * zero rows rather than throw - so the row is left exactly where {@link JdbcOutbox#claim} put it, {@code SENDING},
     * forever, rather than ending up either {@code SENT} or an error. This is what would happen to EVERY row
     * {@link OutboxDrain} claimed if {@code OrganizationScope.runAs} in {@link CommunicationSweep} ever stopped
     * wrapping the per-organization unit, or wrapped only {@link SweepUnit#drain}'s own now-nonexistent transaction
     * instead of the calls it makes - silently, with no exception anywhere, which is exactly why reading
     * {@code current_setting} back over a superuser connection is not a sufficient proof on its own.
     */
    @Test
    void anUnscopedRowProcessorCallLeavesTheRowUnmarked() throws SQLException {

        var org = uniqueOrg();
        seedOutboxRow(org, "user-1");
        var claimed = OrganizationScope.runAs(org, () -> outbox.claim(claimQuery())).getFirst();
        stubRowProcessorCollaboratorsFor(org, "user-1");

        rowProcessor.process(claimed, mock(MailBatch.class));

        assertThat(statusOf(claimed.getId())).isEqualTo("SENDING");
    }

    /**
     * The one test that proves the drain's worker scope where it actually bites. {@code DrainWorkerScopeTest} proves
     * the value a worker thread's {@code SecurityContext} holds; it cannot prove that value ever reaches
     * {@code app.current_org} on the connection the worker's own transaction acquires - and an owner-connected test
     * cannot prove it either, because the Testcontainers superuser bypasses RLS by table ownership and would mark every
     * row {@code SENT} with the scope wired to nothing at all. This is the {@code axonia_app} half.
     *
     * <p>
     * Eight rows against {@code drainWorkers} of four is deliberately more than one slice: a single-slice drain would
     * be indistinguishable from a serial drain, and it is precisely the SECOND and later worker - the threads
     * {@code OrganizationScope} was never going to reach on its own - that this test is about. The connection count
     * asserted below is what makes "more than one slice genuinely ran" an assertion rather than an assumption about how
     * {@code ceilDiv} happened to divide.
     *
     * <p>
     * The {@code SENT} assertion is not vacuously green: {@link #anUnscopedRowProcessorCallLeavesTheRowUnmarked}
     * demonstrates on this same class's same connection that an unscoped {@code markSent} touches zero rows silently
     * and leaves the row exactly where {@code claim} put it, {@code SENDING}. Eight rows all reading {@code SENT} is
     * therefore eight workers' own transactions each having genuinely carried the organization scope to the database.
     */
    @Test
    void aDrainAcrossSeveralWorkersMarksEveryRowSentThroughTheRealRlsRole() throws SQLException {

        var org = uniqueOrg();
        var users = List.of("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7", "user-8");
        for (var user : users) {
            seedOutboxRow(org, user);
        }
        stubRowProcessorCollaboratorsForEveryUserOf(org);
        var connections = new AtomicInteger();
        when(delivery.open()).thenAnswer(invocation -> countedNoOpBatch(connections));

        OrganizationScope.runAs(org, () -> drain.drain(Instant.now().plus(Duration.ofMinutes(1))));

        assertThat(connections.get()).isGreaterThan(1);
        assertThat(statusesOf(org)).hasSize(8).allSatisfy(status -> assertThat(status).isEqualTo("SENT"));
    }

    /**
     * The calling thread's own scope survives the workers, which matters because it is not decoration: this thread
     * issues {@code markFailed} and {@code release} once the workers come back, and both are RLS-scoped writes that
     * would silently touch zero rows if a worker had cleared or overwritten what it found here.
     */
    @Test
    void aDrainLeavesTheCallingThreadsOwnScopeIntact() throws SQLException {

        var org = uniqueOrg();
        seedOutboxRow(org, "user-1");
        seedOutboxRow(org, "user-2");
        stubRowProcessorCollaboratorsForEveryUserOf(org);
        when(delivery.open()).thenAnswer(invocation -> countedNoOpBatch(new AtomicInteger()));

        OrganizationScope.runAs(org, () -> {
            drain.drain(Instant.now().plus(Duration.ofMinutes(1)));
            assertThat(jdbc.queryForObject("SELECT current_setting('app.current_org', true)", Map.of(), String.class))
                    .isEqualTo(org);
        });
    }

    private void stubRowProcessorCollaboratorsForEveryUserOf(String organizationId) {

        when(gate.admit(any())).thenReturn(Optional.empty());
        when(userDirectory.byIds(eq(organizationId), any())).thenAnswer(invocation -> {
            List<String> userIds = invocation.getArgument(1);
            return userIds.stream()
                    .map(id -> new DirectoryUser(id, id, id + "@example.com", null, null, MemberStatus.PRESENT))
                    .toList();
        });
        when(renderer.render(any(), any())).thenReturn(new RenderedMail("Subject", "<p>html</p>", "text"));
        when(unsubscribeTokens.sign(any(), any(), any())).thenReturn("signed-token");
    }

    /**
     * A hand-written batch rather than a Mockito mock: four worker threads sending through their own connections at
     * once is exactly the shape where a shared mock's invocation recording buys nothing and costs contention.
     */
    private static MailBatch countedNoOpBatch(AtomicInteger connections) {

        connections.incrementAndGet();
        return new MailBatch() {

            @Override
            public void send(OutgoingMail mail) {
            }

            @Override
            public void close() {
            }
        };
    }

    /** Superuser connection, for the same reason {@link #statusOf} uses one: the row's true state, RLS or not. */
    private static List<String> statusesOf(String organizationId) throws SQLException {

        try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var stmt = conn
                        .prepareStatement("SELECT status FROM communication_delivery WHERE organization_id = ?")) {
            stmt.setString(1, organizationId);
            try (var rs = stmt.executeQuery()) {
                var statuses = new ArrayList<String>();
                while (rs.next()) {
                    statuses.add(rs.getString(1));
                }
                return statuses;
            }
        }
    }

    /**
     * {@link JdbcOutbox#release} is the last write in this class with no {@code axonia_app} coverage of its own: a
     * standalone, non-{@code @Transactional} {@code jdbc.update} that {@link OutboxDrain#releaseRemaining} calls on the
     * way out of a graceful shutdown. Its {@code UPDATE} carries the identical explicit {@code organization_id = :org}
     * shape {@code markSent} does, so the same reasoning applies: the ONLY thing that can make it touch zero rows under
     * {@code axonia_app} is RLS hiding the row from a connection with no {@code app.current_org} set.
     */
    @Test
    void aScopedReleaseMovesItsOwnRowBackToPending() throws SQLException {

        var org = uniqueOrg();
        seedOutboxRow(org, "user-1");
        var claimed = OrganizationScope.runAs(org, () -> outbox.claim(claimQuery())).getFirst();

        OrganizationScope.runAs(org, () -> outbox.release(List.of(claimed.getId()), org, NOW));

        assertThat(statusOf(claimed.getId())).isEqualTo("PENDING");
    }

    /**
     * The negative half of {@link #aScopedReleaseMovesItsOwnRowBackToPending}: the identical row, released with no
     * {@code OrganizationScope} active. Left {@code SENDING} rather than {@code PENDING} - a row a graceful shutdown
     * tried to release back stays claimed until {@code claimLease} expires instead, the exact silent-strand failure
     * mode this whole feature exists to avoid, if this scope were ever wired wrong.
     */
    @Test
    void anUnscopedReleaseLeavesTheRowSending() throws SQLException {

        var org = uniqueOrg();
        seedOutboxRow(org, "user-1");
        var claimed = OrganizationScope.runAs(org, () -> outbox.claim(claimQuery())).getFirst();

        outbox.release(List.of(claimed.getId()), org, NOW);

        assertThat(statusOf(claimed.getId())).isEqualTo("SENDING");
    }

    private void stubRowProcessorCollaboratorsFor(String organizationId, String userId) {

        when(gate.admit(any())).thenReturn(Optional.empty());
        when(userDirectory.byIds(organizationId, List.of(userId))).thenReturn(
                List.of(new DirectoryUser(userId, userId, "ada@example.com", null, null, MemberStatus.PRESENT)));
        when(renderer.render(any(), any())).thenReturn(new RenderedMail("Subject", "<p>html</p>", "text"));
        when(unsubscribeTokens.sign(any(), any(), any())).thenReturn("signed-token");
    }

    /**
     * Superuser connection, deliberately, not the {@code axonia_app}-scoped {@link #jdbc}: this read must see the row's
     * true state regardless of whether {@code app.current_org} was ever set for it, which is the entire point of
     * {@link #anUnscopedRowProcessorCallLeavesTheRowUnmarked} - a read through {@link #jdbc} with no scope active would
     * itself be hidden by RLS and this method would never even get a row back to compare.
     */
    private static String statusOf(Long id) throws SQLException {

        try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var stmt = conn.prepareStatement("SELECT status FROM communication_delivery WHERE id = ?")) {
            stmt.setLong(1, id);
            try (var rs = stmt.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private static String uniqueOrg() {

        return "org-" + SEQ.incrementAndGet();
    }

    private static Outbox.ClaimQuery claimQuery() {

        return Outbox.ClaimQuery.builder().batchSize(10).now(NOW).maxAttempts(5).retryBackoff(Duration.ofMinutes(5))
                .claimLease(Duration.ofMinutes(5)).build();
    }

    /**
     * Superuser connection, deliberately bypassing RLS: this is fixture setup, not the behaviour under test. Returns
     * the generated {@code subject_ref} so a caller that needs to read the row back through {@link JdbcOutbox}, such as
     * {@link #anUnscopedOutcomeReadSeesAllZeros}, does not have to reconstruct it.
     */
    private static String seedOutboxRow(String organizationId, String userId) throws SQLException {

        var subjectRef = organizationId + "-" + userId + "-" + ROW_SEQ.incrementAndGet();
        seedOutboxRow(organizationId, subjectRef, userId);
        return subjectRef;
    }

    /**
     * Overload for a caller that needs a specific {@code subjectRef} -
     * {@link #aScopedOutcomeReadCountsItsOwnOrganizationOnly} seeds two organizations under the SAME subject, which the
     * generated-subject overload above cannot express.
     */
    private static void seedOutboxRow(String organizationId, String subjectRef, String userId) throws SQLException {

        var now = Timestamp.from(NOW);
        try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            long messageId;
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO communication_message
                        (organization_id, message_type, subject_ref, template, facts, created_at, updated_at, version)
                    VALUES (?, ?, ?, 'session-wrap-up', '{}'::jsonb, ?, ?, 0)
                    RETURNING id
                    """)) {
                stmt.setString(1, organizationId);
                stmt.setString(2, MessageType.SESSION_WRAP_UP.code());
                stmt.setString(3, subjectRef);
                stmt.setTimestamp(4, now);
                stmt.setTimestamp(5, now);
                try (var rs = stmt.executeQuery()) {
                    rs.next();
                    messageId = rs.getLong(1);
                }
            }
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO communication_delivery
                        (organization_id, message_id, user_id, status, created_at, updated_at, version)
                    VALUES (?, ?, ?, 'PENDING', ?, ?, 0)
                    """)) {
                stmt.setString(1, organizationId);
                stmt.setLong(2, messageId);
                stmt.setString(3, userId);
                stmt.setTimestamp(4, now);
                stmt.setTimestamp(5, now);
                stmt.executeUpdate();
            }
        }
    }

    /** Superuser connection, deliberately bypassing RLS: this is fixture setup, not the behaviour under test. */
    private static void seedPreferenceRow(String organizationId, String userId, MessageType messageType)
            throws SQLException {

        try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var stmt = conn.prepareStatement("""
                        INSERT INTO communication_preference
                            (organization_id, user_id, message_type, enabled, created_at, updated_at, version)
                        VALUES (?, ?, ?, false, ?, ?, 0)
                        """)) {
            var now = Timestamp.from(NOW);
            stmt.setString(1, organizationId);
            stmt.setString(2, userId);
            stmt.setString(3, messageType.code());
            stmt.setTimestamp(4, now);
            stmt.setTimestamp(5, now);
            stmt.executeUpdate();
        }
    }

    /** Superuser connection, deliberately bypassing RLS: this is fixture setup, not the behaviour under test. */
    private static void seedWatermarkRow(String organizationId, String producerKey, Instant producedThrough)
            throws SQLException {

        try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var stmt = conn.prepareStatement("""
                        INSERT INTO communication_production_watermark
                            (organization_id, producer_key, produced_through, updated_at, version)
                        VALUES (?, ?, ?, ?, 0)
                        """)) {
            stmt.setString(1, organizationId);
            stmt.setString(2, producerKey);
            stmt.setTimestamp(3, Timestamp.from(producedThrough));
            stmt.setTimestamp(4, Timestamp.from(NOW));
            stmt.executeUpdate();
        }
    }

    /** Superuser connection: the positive control for the unscoped outbox test, not the behaviour under test. */
    private static long countOutboxRows(String organizationId) throws SQLException {

        try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var stmt = conn
                        .prepareStatement("SELECT count(*) FROM communication_delivery WHERE organization_id = ?")) {
            stmt.setString(1, organizationId);
            try (var rs = stmt.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Superuser connection: the message half of {@link #countOutboxRows}, a positive control, not behaviour. */
    private static long countMessageRows(String organizationId) throws SQLException {

        try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var stmt = conn
                        .prepareStatement("SELECT count(*) FROM communication_message WHERE organization_id = ?")) {
            stmt.setString(1, organizationId);
            try (var rs = stmt.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Superuser connection: the positive control for the unscoped preferences test, not the behaviour under test. */
    private static long countPreferenceRows(String organizationId) throws SQLException {

        try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var stmt = conn
                        .prepareStatement("SELECT count(*) FROM communication_preference WHERE organization_id = ?")) {
            stmt.setString(1, organizationId);
            try (var rs = stmt.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
