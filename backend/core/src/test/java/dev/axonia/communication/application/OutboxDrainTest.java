package dev.axonia.communication.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;

import dev.axonia.communication.adapter.out.persistence.JdbcOutbox;
import dev.axonia.communication.application.port.out.MailBatch;
import dev.axonia.communication.application.port.out.MailDelivery;
import dev.axonia.communication.application.port.out.Outbox;
import dev.axonia.communication.configuration.CommunicationPropertiesMother;
import dev.axonia.communication.domain.OutboxDelivery;

import static dev.axonia.communication.application.DrainWorkers.Outcome.ACCOUNT_REFUSED;
import static dev.axonia.communication.application.DrainWorkers.Outcome.FAILED;
import static dev.axonia.communication.application.DrainWorkers.Outcome.HELD;
import static dev.axonia.communication.application.DrainWorkers.Outcome.NOT_ATTEMPTED;
import static dev.axonia.communication.application.DrainWorkers.Outcome.PROCESSED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Claims a batch and orchestrates it: this class no longer knows how a row is processed - that is
 * {@link OutboxRowProcessor}'s job - nor on which thread, nor over which connection - that is {@link DrainWorkers}'
 * job, exercised in {@link DrainWorkersTest} and {@link DrainWorkerScopeTest}. What is left here, and what this class
 * tests, is the bookkeeping the calling thread must own because it is the only thread with no transaction of its own:
 * marking the rows the workers reported failed, releasing the rows they never sent, and deciding whether to claim
 * again.
 *
 * <p>
 * Two behaviours this class used to pin now live where the code that decides them moved to. That one row's own
 * {@link org.springframework.dao.DataAccessException} cannot stop the batch - a database error - is
 * {@code DrainWorkersTest.aRowWhoseProcessingThrowsADataAccessExceptionDoesNotStopItsSlice}; that the mail connection
 * is closed even when a row fails is {@code DrainWorkersTest.theConnectionIsClosedEvenWhenARowFails}. Neither guarantee
 * was dropped, and the other half - that {@code markFailed} lands on a connection the failure never touched - is still
 * proven against a real database by {@link OutboxDrainTransactionIsolationTest}.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
public class OutboxDrainTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String ORG = "org-1";

    @Mock
    private JdbcOutbox outbox;
    @Mock
    private DrainWorkers workers;
    @Mock
    private MailDelivery delivery;
    @Mock
    private MailBatch batch;
    @Mock
    private SendPacing pacing;
    @Captor
    private ArgumentCaptor<Supplier<MailBatch>> batches;
    @Captor
    private ArgumentCaptor<DrainWorkers.SendWindow> windows;
    @Captor
    private ArgumentCaptor<BooleanSupplier> stopFlags;
    @Captor
    private ArgumentCaptor<Outbox.ClaimQuery> claims;

    private Clock clock;
    private OutboxDrain drain;
    private Instant deadline;

    @BeforeEach
    void setUp() {

        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var properties = CommunicationPropertiesMother.any();
        drain = new OutboxDrain(outbox, workers, delivery, clock, properties, pacing);
        deadline = NOW.plus(properties.drainBudget());
        lenient().when(delivery.open()).thenReturn(batch);
        lenient().when(pacing.awaitSendSlot(any(), any())).thenReturn(true);
    }

    /**
     * Returns {@code rows} once and then an empty list forever after: with the fixed clock this class tests against,
     * the deadline never itself ends the loop, so a stub that kept returning the same non-empty batch would loop until
     * the JVM was killed rather than until the batch was actually drained.
     */
    private void givenClaimable(OutboxDelivery... rows) {

        when(outbox.claim(any(Outbox.ClaimQuery.class))).thenReturn(List.of(rows)).thenReturn(List.of());
    }

    private void givenWorkersReporting(DrainWorkers.Outcome... outcomes) {

        when(workers.runAll(any(), any(), any(), any())).thenReturn(List.of(outcomes));
    }

    /** What a real worker does when the mail server takes a message of the batch: it says so, then answers. */
    private void givenWorkersHavingMailAccepted(DrainWorkers.Outcome... outcomes) {

        when(workers.runAll(any(), any(), any(), any())).thenAnswer(invocation -> {
            DrainWorkers.SendWindow window = invocation.getArgument(3);
            window.acceptance().accepted();
            return List.of(outcomes);
        });
    }

    /**
     * Stands in for the pool the way a real worker behaves rather than by returning a canned list: it asks the
     * admission gate once per row, stops asking the moment it refuses, and reports every row behind that point
     * {@code NOT_ATTEMPTED}. Only a stand-in that actually consults the gate can tell "the drain closes its gate on a
     * shutdown" apart from "the drain hands the workers a gate nothing ever reads" - and the gate carries the shutdown
     * flag and nothing else now, which is what {@link #thePaceNeverEndsATickTheWayAShutdownDoes} pins from the other
     * side. {@code afterEachRow} runs for each row the gate admitted, which is where a test signals something - a
     * shutdown - arriving mid-batch.
     */
    private void givenWorkersHonouringTheAdmissionGate(Consumer<OutboxDelivery> afterEachRow) {

        when(workers.runAll(any(), any(), any(), any())).thenAnswer(invocation -> {
            List<OutboxDelivery> rows = invocation.getArgument(1);
            DrainWorkers.SendWindow window = invocation.getArgument(3);
            var outcomes = new ArrayList<DrainWorkers.Outcome>();
            var admitting = true;
            for (var row : rows) {
                admitting = admitting && window.draining().getAsBoolean();
                outcomes.add(admitting ? PROCESSED : NOT_ATTEMPTED);
                if (admitting) {
                    afterEachRow.accept(row);
                }
            }
            return outcomes;
        });
    }

    private static OutboxDelivery row(long id) {

        return OutboxDelivery.builder().id(id).organizationId(ORG).status("SENDING").build();
    }

    /**
     * Advances by {@code step} on every {@link #instant()} read, unlike the fixed clock the rest of this class tests
     * against. Only a clock that actually moves can distinguish "the loop rechecks its deadline every iteration" from
     * "the deadline is glanced at once on entry and never rechecked again" - with a fixed clock the two are
     * indistinguishable, since {@code isBefore(deadline)} never turns false on its own.
     */
    private static final class SteppingClock extends Clock {

        private Instant current;
        private final Duration step;

        SteppingClock(Instant start, Duration step) {

            this.current = start;
            this.step = step;
        }

        @Override
        public ZoneId getZone() {

            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {

            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {

            var now = current;
            current = current.plus(step);
            return now;
        }
    }

    @Test
    void aRowThatProcessesCleanlyIsNeverMarkedFailed() {

        givenClaimable(row(1L));
        givenWorkersReporting(PROCESSED);

        drain.drain(deadline);

        verify(outbox, never()).markFailed(anyLong(), any(), any(), anyInt());
        verify(outbox, never()).release(any(), any(), any());
    }

    /**
     * A whole claim goes to the workers in one call, under the organization the claim itself carries: the drain reads
     * no scope of its own and hands nothing row by row, which is what lets a worker open one connection for a slice.
     */
    @Test
    void handsTheWholeClaimToTheWorkersUnderTheClaimsOwnOrganization() {

        var rows = List.of(row(1L), row(2L), row(3L));
        when(outbox.claim(any(Outbox.ClaimQuery.class))).thenReturn(rows).thenReturn(List.of());
        givenWorkersReporting(PROCESSED, PROCESSED, PROCESSED);

        drain.drain(deadline);

        verify(workers).runAll(eq(ORG), eq(rows), any(), any());
    }

    /**
     * The connection is opened by a worker, inside that worker's own organization scope, not by this thread: what the
     * drain hands down is the means to open one, and it never opens one itself. How many that costs - one per worker,
     * never one per row - is {@code DrainWorkersTest.opensOneMailConnectionPerWorkerRatherThanPerRow}.
     */
    @Test
    void theWorkersAreHandedTheMailDeliverysOwnConnectionToOpen() {

        givenClaimable(row(1L));
        givenWorkersReporting(PROCESSED);

        drain.drain(deadline);

        verify(delivery, never()).open();
        verify(workers).runAll(any(), any(), batches.capture(), any());
        assertThat(batches.getValue().get()).isSameAs(batch);
    }

    /**
     * The failure bookkeeping belongs to this thread, and only to the rows the workers actually reported failed: a
     * {@code markFailed} opens a connection of its own, one that was never party to whatever went wrong in the worker -
     * see {@link OutboxRowProcessor}'s javadoc for why that separation contains a row's database error. Row 1's mark
     * already committed on its own transaction before row 2 was ever attempted, so nothing row 2 does can touch it.
     */
    @Test
    void marksOnlyTheRowsTheWorkersReportedAsFailed() {

        givenClaimable(row(1L), row(2L));
        givenWorkersReporting(PROCESSED, FAILED);

        drain.drain(deadline);

        verify(outbox).markFailed(2L, ORG, NOW, 5);
        verify(outbox, never()).markFailed(eq(1L), any(), any(), anyInt());
        verify(outbox, never()).release(any(), any(), any());
    }

    /**
     * The stop flag is checked between rows, not inside one, and it now reaches the workers as the admission gate they
     * ask before each row: a shutdown signalled while row 1 is still being processed lets row 1 reach a terminal state,
     * and only then stops before row 2 is ever touched. Row 2 is released straight back to PENDING rather than left
     * claimed (SENDING) for the full claimLease - a graceful redeploy costs nothing instead of merely surviving.
     */
    @Test
    void shutdownMidBatchFinishesTheInFlightRowAndReleasesTheRest() {

        givenClaimable(row(1L), row(2L));
        givenWorkersHonouringTheAdmissionGate(row -> drain.onShutdown());

        drain.drain(deadline);

        verify(outbox).release(List.of(2L), ORG, NOW);
        verify(outbox, never()).markFailed(anyLong(), any(), any(), anyInt());
        verify(outbox, times(1)).claim(any());
    }

    /**
     * A shutdown arriving after the last row of a batch has nothing left to release - every row came back
     * {@code PROCESSED} - and the tick still ends, because {@link OutboxDrain#drain}'s own loop guard refuses to claim
     * again. What this pins is that a fully-processed batch releases nothing: a release call here would mean rows the
     * workers had genuinely sent were being handed back to a later tick to send a second time.
     */
    @Test
    void aFullyProcessedBatchReleasesNothingEvenIfAShutdownFollowsIt() {

        givenClaimable(row(1L));
        givenWorkersHonouringTheAdmissionGate(row -> drain.onShutdown());

        drain.drain(deadline);

        verify(outbox, times(1)).claim(any());
        verify(outbox, never()).release(any(), any(), any());
    }

    /**
     * The two halves of the bookkeeping must not be able to take each other down. {@link JdbcOutbox#markFailed} is
     * deliberately unguarded - a failure recording a failure has always surfaced - but it now shares a method with the
     * release, so a transient {@code DataAccessException} out of the mark must not also strand the rows a shutdown or a
     * refusal had already decided to hand straight back. Releasing first makes them independent, and this is what pins
     * the order.
     */
    @Test
    void aFailingMarkStillLeavesTheUnsentRowsReleased() {

        givenClaimable(row(1L), row(2L));
        givenWorkersReporting(FAILED, NOT_ATTEMPTED);
        doThrow(new DataIntegrityViolationException("simulated database failure")).when(outbox).markFailed(anyLong(),
                any(), any(), anyInt());

        assertThatThrownBy(() -> drain.drain(deadline)).isInstanceOf(DataIntegrityViolationException.class);

        verify(outbox).release(List.of(2L), ORG, NOW);
    }

    /**
     * The release is best-effort: a shutdown must never turn into an uncaught exception just because the release call
     * itself failed. The row falls back to the ordinary lease-expiry path {@code JdbcOutbox.claim} already tolerates.
     */
    @Test
    void aReleaseFailureDuringShutdownIsSwallowedNotPropagated() {

        givenClaimable(row(1L), row(2L));
        givenWorkersHonouringTheAdmissionGate(row -> drain.onShutdown());
        doThrow(new RuntimeException("boom")).when(outbox).release(any(), any(), any());

        drain.drain(deadline);

        verify(outbox, times(1)).claim(any());
    }

    /**
     * A refusal about the account itself - a quota, a rejected credential, a refused sender - where every remaining
     * mail row would refuse identically. Row 1 is released back to {@code PENDING} along with the mail rows held behind
     * it, rather than marked failed: it was never actually sent, and {@link OutboxRowProcessor#process}'s own
     * {@code markSent} was rolled back with its transaction. The {@link Timeout} is the backstop for a drain that keeps
     * re-claiming the mail it just held.
     */
    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void anAccountRefusalReleasesTheHeldMailWithoutBurningAnAttempt() {

        givenClaimable(row(1L), row(2L), row(3L));
        givenWorkersReporting(ACCOUNT_REFUSED, HELD, HELD);

        drain.drain(deadline);

        verify(outbox, never()).markFailed(anyLong(), any(), any(), anyInt());
        verify(outbox).release(List.of(1L, 2L, 3L), ORG, NOW);
    }

    /**
     * A refused mail server stops mail and nothing else. The drain goes back for the organization's other rows in the
     * same tick, and once a batch met a refusal with no mail accepted, the claims it makes from then on leave its mail
     * where it is.
     */
    @Test
    void aMailRefusalGoesOnClaimingTheTicksOtherRowsWithTheMailHeld() {

        givenClaimable(row(1L), row(2L));
        givenWorkersReporting(ACCOUNT_REFUSED, HELD);

        drain.drain(deadline);

        verify(outbox, times(2)).claim(claims.capture());
        assertThat(claims.getAllValues()).extracting(Outbox.ClaimQuery::mailHeld).containsExactly(false, true);
    }

    /**
     * Once held, the mail stays held for the rest of the tick: a later batch of in-app entries and chat posts, which
     * meets no refusal because it holds no mail, says nothing about whether the mail server would take mail again.
     */
    @Test
    void aHeldTickKeepsItsMailHeldThroughTheBatchesThatFollow() {

        when(outbox.claim(any(Outbox.ClaimQuery.class))).thenReturn(List.of(row(1L))).thenReturn(List.of(row(2L)))
                .thenReturn(List.of());
        when(workers.runAll(any(), any(), any(), any())).thenReturn(List.of(ACCOUNT_REFUSED))
                .thenReturn(List.of(PROCESSED));

        drain.drain(deadline);

        verify(outbox, times(3)).claim(claims.capture());
        assertThat(claims.getAllValues()).extracting(Outbox.ClaimQuery::mailHeld).containsExactly(false, true, true);
    }

    /**
     * A refusal while another worker's mail was accepted is a server refusing one connection, not the mail: the refused
     * rows go back unmarked and the drain goes on claiming mail, rather than holding it all for a server still taking
     * it.
     */
    @Test
    void aMailRefusalBesideAcceptedMailGoesOnClaimingTheMail() {

        givenClaimable(row(1L), row(2L));
        givenWorkersHavingMailAccepted(ACCOUNT_REFUSED, PROCESSED);

        drain.drain(deadline);

        verify(outbox, times(2)).claim(claims.capture());
        assertThat(claims.getAllValues()).extracting(Outbox.ClaimQuery::mailHeld).containsExactly(false, false);
        verify(outbox).release(List.of(1L), ORG, NOW);
        verify(outbox, never()).markFailed(anyLong(), any(), any(), anyInt());
    }

    /**
     * A server refusing one connection of several refuses one in every batch, and the drain goes on claiming mail
     * through all of them: the refusal is said once per tick, not once per batch, or a backlog behind a connection cap
     * would log the same line dozens of times a tick.
     */
    @Test
    void aTickWhoseBatchesAreRefusedBesideAcceptedMailWarnsOnce(CapturedOutput output) {

        when(outbox.claim(any(Outbox.ClaimQuery.class))).thenReturn(List.of(row(1L), row(2L)))
                .thenReturn(List.of(row(3L), row(4L))).thenReturn(List.of());
        givenWorkersHavingMailAccepted(ACCOUNT_REFUSED, PROCESSED);

        drain.drain(deadline);

        verify(workers, times(2)).runAll(any(), any(), any(), any());
        assertThat(output.getOut().split("The mail server refused this organization's mail", -1)).hasSize(2);
    }

    /**
     * Whether mail was accepted is a fact about one batch, not about the tick: a second batch the server refuses
     * outright holds the mail even though the batch before it had mail accepted. A send window shared across the tick's
     * batches would carry the first batch's acceptance into the second and never hold.
     */
    @Test
    void aBatchRefusedOutrightHoldsTheMailEvenAfterABatchThatHadMailAccepted() {

        when(outbox.claim(any(Outbox.ClaimQuery.class))).thenReturn(List.of(row(1L), row(2L)))
                .thenReturn(List.of(row(3L))).thenReturn(List.of());
        when(workers.runAll(any(), any(), any(), any())).thenAnswer(invocation -> {
            DrainWorkers.SendWindow window = invocation.getArgument(3);
            window.acceptance().accepted();
            return List.of(ACCOUNT_REFUSED, PROCESSED);
        }).thenReturn(List.of(ACCOUNT_REFUSED));

        drain.drain(deadline);

        verify(outbox, times(3)).claim(claims.capture());
        assertThat(claims.getAllValues()).extracting(Outbox.ClaimQuery::mailHeld).containsExactly(false, false, true);
    }

    /** The hold lasts one tick: the next one claims the held mail again, with no attempt spent on it meanwhile. */
    @Test
    void theNextTickClaimsTheHeldMailAgain() {

        when(outbox.claim(any(Outbox.ClaimQuery.class))).thenReturn(List.of(row(1L))).thenReturn(List.of())
                .thenReturn(List.of(row(1L))).thenReturn(List.of());
        when(workers.runAll(any(), any(), any(), any())).thenReturn(List.of(ACCOUNT_REFUSED))
                .thenReturn(List.of(PROCESSED));

        drain.drain(deadline);
        drain.drain(deadline);

        verify(outbox, times(4)).claim(claims.capture());
        assertThat(claims.getAllValues()).extracting(Outbox.ClaimQuery::mailHeld).containsExactly(false, true, false,
                false);
        verify(outbox).release(List.of(1L), ORG, NOW);
        verify(outbox, never()).markFailed(anyLong(), any(), any(), anyInt());
    }

    /**
     * The shape a refused CONNECTION produces rather than a refused message: no row was ever attempted, so every row a
     * worker was holding comes back refused at once. The whole claim is released rather than left {@code SENDING} until
     * its lease expires - a connection nobody can open would refuse every one of those rows identically, and a
     * sustained provider outage must not strand a fresh batch on every tick.
     */
    @Test
    void aRefusedConnectionReleasesTheWholeClaimWithoutAttemptingARow() {

        givenClaimable(row(1L), row(2L));
        givenWorkersReporting(ACCOUNT_REFUSED, ACCOUNT_REFUSED);

        drain.drain(deadline);

        verify(outbox, never()).markFailed(anyLong(), any(), any(), anyInt());
        verify(outbox).release(List.of(1L, 2L), ORG, NOW);
    }

    /**
     * The send pace is no longer a verdict this class hands the workers, and this is what pins that. The gate asked
     * before each row carries the shutdown flag alone, so a tick that is merely being paced admits every row it claimed
     * and goes back for more - where the pace used to sit here too, one spent bucket ended the tick and left the rest
     * of the queue waiting a whole drain-interval, which made a backlogged organization slower than before there was
     * any concurrency at all.
     */
    @Test
    void thePaceNeverEndsATickTheWayAShutdownDoes() {

        when(outbox.claim(any(Outbox.ClaimQuery.class))).thenReturn(List.of(row(1L), row(2L)))
                .thenReturn(List.of(row(3L))).thenReturn(List.of());
        givenWorkersHonouringTheAdmissionGate(row -> {
        });

        drain.drain(deadline);

        verify(outbox, times(3)).claim(any());
        verify(outbox, never()).release(any(), any(), any());
        verify(outbox, never()).markFailed(anyLong(), any(), any(), anyInt());
    }

    /**
     * The window a worker paces against is the tick's own deadline, computed once on entry, so a worker waiting for a
     * send slot can never outlast the budget that claimed its row - and cannot disagree with it either, which reading
     * the clock a second time inside the worker would allow.
     */
    @Test
    void theSendWindowPacesAgainstTheTicksOwnDeadline() {

        givenClaimable(row(1L));
        givenWorkersReporting(PROCESSED);

        drain.drain(deadline);

        verify(workers).runAll(any(), any(), any(), windows.capture());
        assertThat(windows.getValue().sendSlot().getAsBoolean()).isTrue();
        verify(pacing).awaitSendSlot(eq(NOW.plus(Duration.ofSeconds(45))), any());
    }

    /**
     * A shutdown reaches the wait itself, not only the gate between rows: a worker already blocked on a send slot must
     * give up rather than sit out the rest of the budget while the context is closing. The flag is handed down live, so
     * it reads what the tick knows now rather than what it knew when the batch was dispatched.
     */
    @Test
    void theSendWindowCarriesTheLiveStopFlagIntoTheWait() {

        givenClaimable(row(1L));
        givenWorkersReporting(PROCESSED);

        drain.drain(deadline);

        verify(workers).runAll(any(), any(), any(), windows.capture());
        windows.getValue().sendSlot().getAsBoolean();
        verify(pacing).awaitSendSlot(any(), stopFlags.capture());
        assertThat(stopFlags.getValue().getAsBoolean()).isFalse();

        drain.onShutdown();

        assertThat(stopFlags.getValue().getAsBoolean()).isTrue();
    }

    /**
     * Confirms the shutdown flag, once set, holds beyond one {@code drain} call: a later call does not even claim a
     * fresh batch, rather than claiming one only to abandon every row in it before touching the first.
     */
    @Test
    void onceStoppedALaterDrainCallClaimsNothingNew() {

        drain.onShutdown();

        drain.drain(deadline);

        verify(outbox, never()).claim(any());
    }

    /**
     * A tick now claims repeatedly rather than once: the third claim comes back empty and that is what ends the loop,
     * not having drained a fixed number of batches.
     */
    @Test
    void drainsRepeatedlyUntilNothingIsClaimable() {

        when(outbox.claim(any(Outbox.ClaimQuery.class))).thenReturn(List.of(row(1L), row(2L)))
                .thenReturn(List.of(row(3L))).thenReturn(List.of());
        when(workers.runAll(any(), any(), any(), any())).thenReturn(List.of(PROCESSED, PROCESSED))
                .thenReturn(List.of(PROCESSED));

        drain.drain(deadline);

        verify(workers, times(2)).runAll(any(), any(), any(), any());
        verify(outbox, times(3)).claim(any());
    }

    /**
     * The signal an operator has that a tenant is falling behind, on the path that now produces it almost every time.
     * At the vendor's ceiling nearly all of a tick's elapsed time is spent inside a worker's wait for a send slot, so
     * the deadline lands mid-batch far more often than between two batches - the batch comes back with rows
     * {@code NOT_ATTEMPTED} and the loop is left through its cut-short exit, not through its own guard. Reported on the
     * way out rather than at the guard, the line survives that.
     */
    @Test
    void reportsTheSpentBudgetEvenWhenTheDeadlineLandsMidBatch(CapturedOutput output) {

        var steppingClock = new SteppingClock(NOW, Duration.ofSeconds(30));
        var budgeted = new OutboxDrain(outbox, workers, delivery, steppingClock, CommunicationPropertiesMother.any(),
                pacing);
        var midBatchDeadline = steppingClock.instant().plus(Duration.ofSeconds(45));
        when(outbox.claim(any(Outbox.ClaimQuery.class))).thenReturn(List.of(row(1L), row(2L)));
        givenWorkersReporting(PROCESSED, NOT_ATTEMPTED);

        budgeted.drain(midBatchDeadline);

        verify(outbox, times(1)).claim(any());
        assertThat(output.getOut()).contains("Outbox drain budget of PT45S spent");
    }

    /**
     * The other half of that rule: a batch cut short while the budget still had time left says nothing about a backlog,
     * so it says nothing at all: rows released with time to spare are not a tenant falling behind.
     */
    @Test
    void saysNothingAboutTheBudgetWhenABatchIsCutShortWithTimeLeft(CapturedOutput output) {

        givenClaimable(row(1L), row(2L));
        givenWorkersReporting(PROCESSED, NOT_ATTEMPTED);

        drain.drain(deadline);

        assertThat(output.getOut()).doesNotContain("Outbox drain budget");
    }

    /**
     * The loop's other exit: a budget already spent before the first claim means no claim ever happens, wall clock
     * rather than a row count is what bounds a tick.
     */
    @Test
    void stopsClaimingOnceTheBudgetIsSpent() {

        var properties = CommunicationPropertiesMother.withDrainBudget(Duration.ZERO);
        var budgeted = new OutboxDrain(outbox, workers, delivery, clock, properties, pacing);
        var alreadySpentDeadline = clock.instant().plus(properties.drainBudget());

        budgeted.drain(alreadySpentDeadline);

        verify(outbox, never()).claim(any());
    }

    /**
     * The case {@link #stopsClaimingOnceTheBudgetIsSpent} cannot cover: the budget ending a tick that still has
     * claimable work, not merely a tick that never started. {@code outbox.claim} always returns a full batch here -
     * never empty - so nothing but the budget itself can end the loop. With the {@link SteppingClock} advancing 10s per
     * read against a 45s budget, the third iteration's own deadline check is the one that reads past budget: two claims
     * land first, proving the check runs on every iteration rather than once on entry. The {@link Timeout} is a safety
     * net, not the assertion - a regression back to "checked once" would make {@code outbox.claim} keep returning a
     * non-empty batch forever and hang rather than fail cleanly.
     */
    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void stopsClaimingOnceTheBudgetIsSpentMidLoop() {

        var steppingClock = new SteppingClock(NOW, Duration.ofSeconds(10));
        var budgeted = new OutboxDrain(outbox, workers, delivery, steppingClock, CommunicationPropertiesMother.any(),
                pacing);
        var midLoopDeadline = steppingClock.instant().plus(Duration.ofSeconds(45));
        when(outbox.claim(any(Outbox.ClaimQuery.class))).thenReturn(List.of(row(1L)));
        givenWorkersReporting(PROCESSED);

        budgeted.drain(midLoopDeadline);

        verify(outbox, times(2)).claim(any());
        verify(workers, times(2)).runAll(any(), any(), any(), any());
    }

    /** On this thread, after every worker is done, so no row's transaction can roll the report back. */
    @Test
    void tellsTheMailServerHowTheBatchWentOnceEveryWorkerIsDone() {

        givenClaimable(row(1L));
        givenWorkersReporting(PROCESSED);

        drain.drain(deadline);

        var order = inOrder(workers, delivery);
        order.verify(workers).runAll(any(), any(), any(), any());
        order.verify(delivery).reportOutcome();
    }

    /** A worker failure that escapes the pool must not leave this batch's outcome for the next batch to report. */
    @Test
    void tellsTheMailServerEvenWhenAWorkerFailureEscapes() {

        givenClaimable(row(1L));
        when(workers.runAll(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("a worker's connection failed to close"));

        assertThatThrownBy(() -> drain.drain(deadline)).isInstanceOf(IllegalStateException.class);

        verify(delivery).reportOutcome();
    }

    @Test
    void tellsTheMailServerEvenWhenMarkingAFailureFails() {

        givenClaimable(row(1L));
        givenWorkersReporting(FAILED);
        doThrow(new QueryTimeoutException("the pool is exhausted")).when(outbox).markFailed(anyLong(), any(), any(),
                anyInt());

        assertThatThrownBy(() -> drain.drain(deadline)).isInstanceOf(QueryTimeoutException.class);

        verify(delivery).reportOutcome();
    }
}
