package dev.axonia.communication.internal;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.OrganizationScope;
import dev.axonia.communication.MessageType;
import dev.axonia.identity.UserDirectory;
import dev.axonia.identity.UserDirectory.DirectoryUser;
import dev.axonia.identity.UserDirectory.MemberStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reproduces, against a real Postgres transaction rather than a mock, the exact failure the batch boundary prevents: a
 * genuine {@code DataAccessException} from inside one row's own processing, not a render or transport failure a mock
 * can fake for free. Under the OLD design - one {@code @Transactional} spanning the whole tick - a real SQL error like
 * the one {@link #aRealDatabaseErrorInOneRowDoesNotPreventTheRestOfTheBatchFromBeingProcessed} forces here would abort
 * that shared transaction at the Postgres level, so the {@code markFailed} meant to record the poisoning row's own
 * failure would run on the SAME now-poisoned connection and fail too - Postgres refuses every further statement on an
 * aborted transaction until it is rolled back - and that second exception would escape the drain uncaught, stopping the
 * loop before the batch's OTHER row was ever reached. This class proves neither half of that happens any more:
 * {@link JdbcOutbox#claim} and {@link OutboxRowProcessor#process} each commit or roll back on their own connection (see
 * both classes' own javadoc), so one row's real SQL error can only ever abort that row's own transaction, regardless of
 * which of the two claimed rows it happens to be - {@code claim}'s {@code RETURNING} clause carries no guarantee that
 * its row order follows the claiming subquery's {@code ORDER BY id}, so this test picks which row poisons itself by
 * user id, never by claim order.
 *
 * <p>
 * Goes in through {@link SweepUnit#drain}, not {@link OutboxDrain#drain} directly, and that is not incidental: this
 * class is what would catch {@code @Transactional} being re-added to {@code SweepUnit.drain} - the exact regression
 * this whole task exists to prevent - because {@code REQUIRED} propagation would then make {@code claim},
 * {@code process} and {@code markFailed} all join that one ambient transaction again instead of each opening their own.
 * Calling {@code drain.drain()} (the {@link OutboxDrain} bean, not the {@link SweepUnit} method of the same name)
 * directly would skip straight past that seam and this test would stay green through the regression. No
 * {@link dev.axonia.communication.MessageProducer} is mocked out: this test never invokes {@link SweepUnit#produce} -
 * {@code produce} and {@code drain} are independent methods on independent schedules - and {@code drain} reaches no
 * producer at all, so the real ones sit inert in the context. A mock could not be installed by type in any case now
 * that more than one module implements that port.
 *
 * <p>
 * Verified by mutation, not merely asserted: temporarily adding {@code @Transactional} back to {@link SweepUnit#drain}
 * turns this exact test red - {@code markFailed} for the poisoning row itself throws {@code BadSqlGrammarException}
 * wrapping "current transaction is aborted", and the other row is left {@code SENDING}, never {@code SENT}, because the
 * loop in {@link OutboxDrain#drain} never reaches it (the escaped exception aborts {@code drain}'s whole transaction).
 * Removing that temporary annotation turns it green again. That perturbation is not committed - a test asserting
 * against code it does not run ships no useful coverage - but it is what makes this test's claim to guard the
 * transaction boundary an evidenced one rather than an assertion about code nobody ran.
 *
 * <p>
 * Runs against the shared {@code jdbc:tc} container like the rest of this package's non-RLS tests: this class proves
 * TRANSACTION ISOLATION, not Row-Level Security, so the superuser connection that bypasses RLS is not a landmine here
 * the way it is for a claimable row left behind for another test to find. It still commits real, permanent rows to that
 * shared table (this is a plain {@code @SpringBootTest}, not a rolled-back {@code @DataJdbcTest} slice), so the class
 * carries the {@code cleanup.sql} {@code @Sql} hook its own header prescribes for exactly this shape of class, rather
 * than leaning solely on a terminal status and a unique organization id to keep other tests from ever seeing what this
 * one leaves behind.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class OutboxDrainTransactionIsolationTest {

    private static final String ORGANIZATION_ID = "org-txn-isolation";
    private static final String ROLLBACK_ORGANIZATION_ID = "org-txn-rollback";
    private static final String REPORT_ORGANIZATION_ID = "org-txn-report";
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
     * A real precondition, not a formality: {@code cleanup.sql} runs {@code AFTER_TEST_CLASS}, not before, so this
     * guards against a previous run's cleanup never having happened (a crashed fork, a manual {@code -Dtest} run that
     * skipped it) rather than the impossible-by-construction case a run-unique organization id would reduce this to.
     * Same landmine {@code SweepUnitScopeBoundaryTest} guards against - see its own javadoc.
     *
     * <p>
     * Called by each test for its OWN organization id rather than run once from {@code @BeforeEach} against a shared
     * one, because both tests here deliberately leave a {@code FAILED} row behind - and {@code FAILED} is claimable. A
     * single shared guard would therefore fail whichever test happened to run second, on rows the test before it was
     * supposed to leave.
     */
    private void assertNoClaimableOutboxRows(String organizationId) {

        var claimable = jdbc.queryForObject(
                "SELECT count(*) FROM communication_delivery WHERE status IN ('PENDING', 'FAILED', 'SENDING') "
                        + "AND organization_id = :org",
                Map.of("org", organizationId), Long.class);
        assertThat(claimable).isZero();
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void aRealDatabaseErrorInOneRowDoesNotPreventTheRestOfTheBatchFromBeingProcessed() {

        assertNoClaimableOutboxRows(ORGANIZATION_ID);
        seedRow(ORGANIZATION_ID, "user-poisons-its-own-row");
        seedRow(ORGANIZATION_ID, "user-sent-after-the-poisoned-row");
        when(userDirectory.byIds(eq(ORGANIZATION_ID), any()))
                .thenReturn(List.of(new DirectoryUser("user-sent-after-the-poisoned-row", "Ada", "ada@example.com",
                        null, null, MemberStatus.PRESENT)));
        when(renderer.render(any(), any())).thenReturn(new RenderedMail("Subject", "<p>html</p>", "text"));
        when(unsubscribeTokens.sign(any(), any(), any())).thenReturn("signed-token");
        // Keyed off the row's own user id, not "the first admit call": claim's RETURNING clause has no documented
        // guarantee that its row order follows the claiming subquery's own ORDER BY id (that ORDER BY exists only to
        // pick WHICH rows the LIMIT selects, not to promise anything about the UPDATE's output order) - relying on call
        // order here would make this test's own row-1-goes-first assumption exactly as fragile as the production bug
        // it exists to reproduce, and it did in fact flip under the full suite's much larger shared table. Whichever
        // row this runs for pays for a genuinely malformed statement on the SAME connection its own per-row transaction
        // acquired (Spring's transaction synchronization binds jdbc's connection to the current thread's active
        // transaction), which Postgres answers by aborting that transaction outright. The OTHER row's admit call, in a
        // fresh transaction with its own connection, is never touched by that abort and admits normally.
        when(delivery.open()).thenReturn(mock(MailBatch.class));
        when(gate.admit(any())).thenAnswer(invocation -> {
            OutboxEntity row = invocation.getArgument(0);
            if ("user-poisons-its-own-row".equals(row.getUserId())) {
                jdbc.getJdbcTemplate().execute("SELECT * FROM no_such_table_in_this_schema_at_all");
            }
            return Optional.empty();
        });

        OrganizationScope.runAs(ORGANIZATION_ID,
                () -> unit.drain(ORGANIZATION_ID, Instant.now().plus(Duration.ofMinutes(1))));

        assertThat(statusOf(ORGANIZATION_ID, "user-poisons-its-own-row")).isEqualTo("FAILED");
        assertThat(statusOf(ORGANIZATION_ID, "user-sent-after-the-poisoned-row")).isEqualTo("SENT");
    }

    /**
     * {@link OutboxRowProcessor#process} writes {@code markSent} BEFORE handing the mail to {@link MailDelivery}, so
     * that a mark which cannot be written fails before anybody receives anything rather than after. That ordering is
     * only safe because the mark and the send share one transaction, and this is the test that proves it against a real
     * Postgres one rather than a mock: the row genuinely reads {@code SENT} from inside the send - the mark is already
     * written, on the same connection, uncommitted - and every trace of that mark is genuinely gone once the transport
     * failure has propagated out and rolled the transaction back. Without the rollback, a row whose mail never left
     * would carry evidence that it had been sent, which is strictly worse than the duplicate the ordering prevents.
     *
     * <p>
     * The rolled-back mark is asserted on {@code sent_at}, not on {@code status}, and that distinction is load-bearing:
     * {@link JdbcOutbox#markFailed} overwrites {@code status} unconditionally, so a row would read {@code FAILED} here
     * whether the mark rolled back or was committed and then overwritten - the status assertion alone proves only the
     * retry path, never the rollback. {@code markFailed} never touches {@code sent_at}, so a {@code sent_at} that
     * survives is a mark that was never rolled back.
     *
     * <p>
     * Verified by mutation, each assertion independently: swapping {@code markSent} back after {@code delivery.send}
     * turns the first red ({@code SENDING}, the claim's own status, no mark having been written yet); removing
     * {@code @Transactional} from {@code process} leaves the status assertion GREEN - which is how the paragraph above
     * was found - and turns the {@code sent_at} one red. Neither perturbation is committed.
     */
    @Test
    void aTransportFailureRollsBackTheSentMarkItAlreadyWrote() {

        assertNoClaimableOutboxRows(ROLLBACK_ORGANIZATION_ID);
        seedRow(ROLLBACK_ORGANIZATION_ID, "user-whose-mark-rolls-back");
        when(userDirectory.byIds(eq(ROLLBACK_ORGANIZATION_ID), any()))
                .thenReturn(List.of(new DirectoryUser("user-whose-mark-rolls-back", "Ada", "ada@example.com", null,
                        null, MemberStatus.PRESENT)));
        when(renderer.render(any(), any())).thenReturn(new RenderedMail("Subject", "<p>html</p>", "text"));
        when(unsubscribeTokens.sign(any(), any(), any())).thenReturn("signed-token");
        when(gate.admit(any())).thenReturn(Optional.empty());
        var statusDuringSend = new AtomicReference<String>();
        var batch = mock(MailBatch.class);
        when(delivery.open()).thenReturn(batch);
        doAnswer(invocation -> {
            statusDuringSend.set(statusOf(ROLLBACK_ORGANIZATION_ID, "user-whose-mark-rolls-back"));
            throw new MailSendException("the transport is down");
        }).when(batch).send(any());

        OrganizationScope.runAs(ROLLBACK_ORGANIZATION_ID,
                () -> unit.drain(ROLLBACK_ORGANIZATION_ID, Instant.now().plus(Duration.ofMinutes(1))));

        assertThat(statusDuringSend.get()).isEqualTo("SENT");
        assertThat(statusOf(ROLLBACK_ORGANIZATION_ID, "user-whose-mark-rolls-back")).isEqualTo("FAILED");
        assertThat(sentAtOf(ROLLBACK_ORGANIZATION_ID, "user-whose-mark-rolls-back")).isNull();
    }

    /**
     * The mail server hears how a batch went with no transaction open: a Self-hosted installation writes that outcome
     * to its settings row, and a transaction opened around the drain - {@code @Transactional} on
     * {@link SweepUnit#drain} or {@link OutboxDrain#drain} - would pull the write into whatever runs there, which this
     * turns red. The row is suppressed so nothing is sent; the report follows every batch regardless.
     */
    @Test
    void theMailServerHearsHowTheBatchWentWithNoTransactionOpen() {

        assertNoClaimableOutboxRows(REPORT_ORGANIZATION_ID);
        seedRow(REPORT_ORGANIZATION_ID, "user-whose-row-is-suppressed");
        when(gate.admit(any())).thenReturn(Optional.of(SuppressionReason.MEMBER_OPTED_OUT));
        var transactionOpen = new AtomicReference<Boolean>();
        doAnswer(invocation -> {
            transactionOpen.set(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(delivery).reportOutcome();

        OrganizationScope.runAs(REPORT_ORGANIZATION_ID,
                () -> unit.drain(REPORT_ORGANIZATION_ID, Instant.now().plus(Duration.ofMinutes(1))));

        assertThat(transactionOpen.get()).isFalse();
        assertThat(statusOf(REPORT_ORGANIZATION_ID, "user-whose-row-is-suppressed")).isEqualTo("SUPPRESSED");
    }

    /**
     * One message per recipient, keyed by a subject_ref of its own, so each row is claimed and marked independently.
     */
    private void seedRow(String organizationId, String userId) {

        var params = new MapSqlParameterSource().addValue("org", organizationId)
                .addValue("type", MessageType.SESSION_WRAP_UP.code()).addValue("subject", "txn-isolation-" + userId)
                .addValue("user", userId).addValue("now", Timestamp.from(NOW));

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

    private String statusOf(String organizationId, String userId) {

        return jdbc.queryForObject(
                "SELECT status FROM communication_delivery WHERE organization_id = :org AND user_id = :user",
                Map.of("org", organizationId, "user", userId), String.class);
    }

    private Timestamp sentAtOf(String organizationId, String userId) {

        return jdbc.queryForObject(
                "SELECT sent_at FROM communication_delivery WHERE organization_id = :org AND user_id = :user",
                Map.of("org", organizationId, "user", userId), Timestamp.class);
    }
}
