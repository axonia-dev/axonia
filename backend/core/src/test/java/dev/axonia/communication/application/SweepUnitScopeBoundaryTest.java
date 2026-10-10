package dev.axonia.communication.application;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.communication.adapter.in.schedule.CommunicationSweep;
import dev.axonia.communication.adapter.out.persistence.JdbcOutbox;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.application.port.out.MailBatch;
import dev.axonia.communication.application.port.out.MailDelivery;
import dev.axonia.identity.api.UserDirectory;
import dev.axonia.identity.api.UserDirectory.DirectoryUser;
import dev.axonia.identity.api.UserDirectory.MemberStatus;
import dev.axonia.shared.api.tenancy.OrganizationScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the wiring, not the enforcement: that {@link CommunicationSweep}'s {@code OrganizationScope.runAs} reaches far
 * enough to cover every transaction {@link SweepUnit#drain} now fans out to, not only a transaction {@code drain}
 * itself no longer opens. {@link SweepUnit#drain} carries no {@code @Transactional} of its own (see its class javadoc)
 * - {@link JdbcOutbox#claim} and {@link OutboxRowProcessor#process} each open one independently, and this class exists
 * to show that both of those, reached only through the real {@link SweepUnit} → {@link OutboxDrain} →
 * {@link OutboxRowProcessor} call chain a production drain tick actually takes, still see the organization id
 * {@code OrganizationScope.runAs} set on entry. No {@link dev.axonia.communication.api.spi.MessageProducer} is mocked
 * out: this test never invokes {@link SweepUnit#produce} - the two are independent methods on independent schedules -
 * and {@code drain} reaches no producer at all, so no producer could make this test pass for the wrong reason. A mock
 * could not be installed by type in any case now that more than one module implements that port.
 *
 * <p>
 * This is deliberately the CHEAP check, not the rigorous one: it runs against the SHARED {@code jdbc:tc} container,
 * whose driver connects as the container superuser and so bypasses Row-Level Security entirely by table ownership - a
 * connection this scoped correctly and one scoped not at all would behave identically as far as RLS is concerned.
 * {@link JdbcOutbox#CLAIM_SQL} and {@code markSent}'s {@code UPDATE} both still carry an explicit
 * {@code organization_id = current_setting('app.current_org', true)} / {@code organization_id = :org} predicate that
 * this container's superuser role does still have to satisfy in plain SQL terms, so a genuinely unset GUC would still
 * leave the seeded row unclaimed here too - but the load-bearing proof that this mechanism survives under RLS itself,
 * against the real least-privilege {@code axonia_app} role, is {@code CommunicationRlsIsolationTest}, not this class.
 * That class's own javadoc explains why a test connected as the schema owner cannot be trusted to prove GUC scoping on
 * its own: it would pass the identical assertions below even if {@code app.current_org} were never set anywhere.
 *
 * <p>
 * Every collaborator {@link OutboxRowProcessor} needs besides {@link JdbcOutbox} is mocked here - {@link DeliveryGate},
 * {@link MailRenderer}, {@link MailDelivery}, {@link UserDirectory}, {@link UnsubscribeTokens} - so this class never
 * touches a real template, a real identity lookup or a real SMTP connection: the only thing under test is whether the
 * organization id makes it all the way from {@link CommunicationSweep}'s wrap down to the {@code UPDATE} that marks the
 * row {@code SENT}, not whether rendering or delivery themselves work.
 *
 * <p>
 * Unlike the ORIGINAL version of this class - which never seeded a row at all, only read {@code current_setting} back
 * during a mocked {@code produce()} - this version drains a real row through to a terminal {@code SENT} status, a real,
 * permanent commit against the shared {@code jdbc:tc} container (this is a plain {@code @SpringBootTest}, not a
 * rolled-back {@code @DataJdbcTest} slice). It therefore carries the {@code cleanup.sql} {@code @Sql} hook its own
 * header prescribes for exactly this shape of class.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class SweepUnitScopeBoundaryTest {

    private static final String ORGANIZATION_ID = "org-scope-boundary";
    private static final String USER_ID = "user-scope-boundary";
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Autowired
    private SweepUnit unit;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

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

    /**
     * Enforces the precondition the class javadoc describes, rather than merely asserting the outcome it happens to
     * produce today: run over this shared superuser connection, so - unlike every RLS-scoped read in this codebase - it
     * genuinely sees every organization's rows, exactly like {@link JdbcOutbox#CLAIM_SQL} would under that same
     * connection. {@code @BeforeEach} rather than the first statement of one test method, so a second method added to
     * this class later is checked too, not left to trust the same assumption silently. A non-zero count here means some
     * other test committed a claimable row for {@code org-scope-boundary} specifically and left it behind - the one
     * case {@link JdbcOutbox#CLAIM_SQL}'s own {@code organization_id} predicate cannot rule out, since a leftover row
     * sharing this exact organization id is, by that predicate's own logic, indistinguishable from a row this class is
     * meant to see.
     */
    @BeforeEach
    void assertNoClaimableOutboxRows() {

        var claimable = jdbc.queryForObject(
                "SELECT count(*) FROM communication_delivery WHERE status IN ('PENDING', 'FAILED', 'SENDING') "
                        + "AND organization_id = :org",
                Map.of("org", ORGANIZATION_ID), Long.class);
        assertThat(claimable).isZero();
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    /**
     * {@code unit.drain} is the only real work here: claim the row seeded below, and run it through the real
     * {@link OutboxRowProcessor#process}. Both {@link JdbcOutbox#claim} and {@code process} open their own transaction
     * (see each class's own javadoc), and both need {@code app.current_org} set on the connection they acquire for
     * {@code SENT} to ever be reached - a gap in the wrap between {@link CommunicationSweep} and either of them would
     * leave this row {@code SENDING} forever instead, the same failure shape {@code CommunicationRlsIsolationTest}'s
     * own unscoped test demonstrates under real RLS.
     */
    @Test
    void theRealTransactionsSeeTheOrganizationScopeSetOnEntry() {

        seedClaimableRow();
        when(gate.admit(any())).thenReturn(Optional.empty());
        when(userDirectory.byIds(ORGANIZATION_ID, List.of(USER_ID))).thenReturn(
                List.of(new DirectoryUser(USER_ID, USER_ID, "ada@example.com", null, null, MemberStatus.PRESENT)));
        when(renderer.render(any(), any())).thenReturn(new RenderedMail("Subject", "<p>html</p>", "text"));
        when(unsubscribeTokens.sign(any(), any(), any())).thenReturn("signed-token");
        when(delivery.open()).thenReturn(mock(MailBatch.class));

        OrganizationScope.runAs(ORGANIZATION_ID,
                () -> unit.drain(ORGANIZATION_ID, Instant.now().plus(Duration.ofMinutes(1))));

        var status = jdbc.queryForObject("SELECT status FROM communication_delivery WHERE organization_id = :org",
                Map.of("org", ORGANIZATION_ID), String.class);
        assertThat(status).isEqualTo("SENT");
    }

    private void seedClaimableRow() {

        var params = new MapSqlParameterSource().addValue("org", ORGANIZATION_ID)
                .addValue("type", MessageType.SESSION_WRAP_UP.code()).addValue("subject", "scope-boundary-42")
                .addValue("user", USER_ID).addValue("now", Timestamp.from(NOW));

        var messageId = jdbc.queryForObject("""
                INSERT INTO communication_message
                    (organization_id, message_type, subject_ref, template, facts, created_at, updated_at, version)
                VALUES (:org, :type, :subject, 'session-wrap-up', '{}'::jsonb, :now, :now, 0)
                RETURNING id
                """, params, Long.class);

        jdbc.update("""
                INSERT INTO communication_delivery
                    (organization_id, message_id, user_id, status, created_at, updated_at, version)
                VALUES (:org, :message, :user, 'PENDING', :now, :now, 0)
                """, params.addValue("message", messageId));
    }
}
