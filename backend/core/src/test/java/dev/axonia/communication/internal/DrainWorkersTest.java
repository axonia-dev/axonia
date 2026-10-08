package dev.axonia.communication.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.communication.ChatDestinationsPort;
import dev.axonia.communication.ChatFailureKind;

import static dev.axonia.communication.internal.DrainWorkers.Outcome.ACCOUNT_REFUSED;
import static dev.axonia.communication.internal.DrainWorkers.Outcome.FAILED;
import static dev.axonia.communication.internal.DrainWorkers.Outcome.HELD;
import static dev.axonia.communication.internal.DrainWorkers.Outcome.NOT_ATTEMPTED;
import static dev.axonia.communication.internal.DrainWorkers.Outcome.PROCESSED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The mechanics of running a claimed batch on a small pool: how rows are split, how many mail connections that costs,
 * and what a worker answers for the rows it was holding when something stopped it. The organization scope every worker
 * runs in - the reason this class exists at all rather than a bare executor - is pinned separately, in
 * {@link DrainWorkerScopeTest}.
 *
 * <p>
 * Every test here asserts an outcome list of exactly the length of the row list it passed in. That is not incidental:
 * {@link OutboxDrain} maps outcomes back onto rows by index, so a slice that answered short would mark one row failed
 * for another row's failure.
 *
 * <p>
 * The row work here sends through the batch it is given wherever the connection count is what is being asserted, the
 * way {@link OutboxRowProcessor#process} does for a row the gate admits. A no-op row work stands for the other real
 * case: a row the gate suppressed, which reaches no transport at all.
 */
class DrainWorkersTest {

    private static final String ORGANIZATION_ID = "acme";

    private static final OutgoingMail MAIL = new OutgoingMail("member@example.com", "Subject", "<p>html</p>", "text",
            null);

    /** Counts what the pool actually costs the mail provider: opens, closes, and the rows each connection carried. */
    private static final class CountingBatches {

        private final AtomicInteger opened = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();

        MailBatch open() {

            opened.incrementAndGet();
            return new MailBatch() {

                @Override
                public void send(OutgoingMail mail) {
                }

                @Override
                public void close() {

                    closed.incrementAndGet();
                }
            };
        }
    }

    /** A tick that is draining normally, with the vendor's pace never in the way. */
    private static DrainWorkers.SendWindow open() {

        return new DrainWorkers.SendWindow(() -> true, () -> true, new MailAcceptance());
    }

    /**
     * The guarantee the batch bought and this pool must not spend: the connection, its TLS negotiation and its
     * authentication are paid once per worker, not once per message. Six rows across two workers cost two connections,
     * not six - and every row still leaves.
     */
    @Test
    void opensOneMailConnectionPerWorkerRatherThanPerRow() {

        var batches = new CountingBatches();
        var sent = new CopyOnWriteArrayList<Long>();
        var workers = poolOf((row, batch) -> {
            batch.send(MAIL);
            sent.add(row.getId());
            return Optional.empty();
        }, 2);

        var outcomes = workers.runAll(ORGANIZATION_ID, rows(1L, 2L, 3L, 4L, 5L, 6L), batches::open, open());

        assertThat(batches.opened).hasValue(2);
        assertThat(batches.closed).hasValue(2);
        assertThat(sent).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(outcomes).containsExactly(PROCESSED, PROCESSED, PROCESSED, PROCESSED, PROCESSED, PROCESSED);
    }

    /**
     * Every message reaches the connection its own worker opened, not some other worker's and not nothing at all.
     * Without this, a batch handle that swallowed its sends would satisfy every other test in this class and in
     * {@link DrainWorkerScopeTest}, and in production every message would be silently dropped.
     */
    @Test
    void everyRowIsSentThroughTheConnectionItsOwnWorkerOpened() {

        var opened = new CopyOnWriteArrayList<MailBatch>();
        var received = new CopyOnWriteArrayList<MailBatch>();
        var workers = poolOf((row, batch) -> {
            batch.send(MAIL);
            return Optional.empty();
        }, 2);

        workers.runAll(ORGANIZATION_ID, rows(1L, 2L, 3L, 4L), () -> recordingBatch(opened, received), open());

        assertThat(opened).hasSize(2);
        assertThat(received).hasSize(4).allSatisfy(batch -> assertThat(opened).contains(batch));
        assertThat(received).containsAll(opened);
    }

    /**
     * No connection outlives the batch it was opened for. Two successive calls open and close two connections, never
     * one held across both - a connection kept alive between claims would look identical to a single-call test and
     * still be wrong, since nothing here would notice the provider dropping an idle socket in between.
     */
    @Test
    void holdsNoConnectionBeyondTheBatchItWasGiven() {

        var batches = new CountingBatches();
        var workers = poolOf((row, batch) -> {
            batch.send(MAIL);
            return Optional.empty();
        }, 1);

        workers.runAll(ORGANIZATION_ID, rows(1L), batches::open, open());
        assertThat(batches.closed).hasValue(1);

        workers.runAll(ORGANIZATION_ID, rows(2L), batches::open, open());

        assertThat(batches.opened).hasValue(2);
        assertThat(batches.closed).hasValue(2);
    }

    /** Fewer rows than workers is fewer slices, not idle workers each opening a connection to send nothing. */
    @Test
    void neverOpensMoreConnectionsThanThereAreRows() {

        var batches = new CountingBatches();
        var workers = poolOf((row, batch) -> {
            batch.send(MAIL);
            return Optional.empty();
        }, 4);

        workers.runAll(ORGANIZATION_ID, rows(1L, 2L), batches::open, open());

        assertThat(batches.opened).hasValue(2);
    }

    /** No rows is no work at all: no thread, no connection, and nothing for the caller to map back. */
    @Test
    void anEmptyBatchOpensNothing() {

        var batches = new CountingBatches();
        var workers = poolOf((row, batch) -> {
            batch.send(MAIL);
            return Optional.empty();
        }, 4);

        var outcomes = workers.runAll(ORGANIZATION_ID, List.of(), batches::open, open());

        assertThat(outcomes).isEmpty();
        assertThat(batches.opened).hasValue(0);
    }

    /**
     * A tick told to stop before its first row opens nothing either. This used to cost a full connect, STARTTLS and
     * AUTH per worker on every graceful shutdown, and - since the send pace was answered through the same gate - on
     * every organization a drained global budget reached.
     */
    @Test
    void aBatchNoRowIsAdmittedToOpensNothing() {

        var batches = new CountingBatches();
        var workers = poolOf((row, batch) -> {
            batch.send(MAIL);
            return Optional.empty();
        }, 2);

        var outcomes = workers.runAll(ORGANIZATION_ID, rows(1L, 2L), batches::open,
                new DrainWorkers.SendWindow(() -> false, () -> true, new MailAcceptance()));

        assertThat(outcomes).containsExactly(NOT_ATTEMPTED, NOT_ATTEMPTED);
        assertThat(batches.opened).hasValue(0);
    }

    /**
     * The other way a slice can send nothing: every row of it suppressed by the delivery gate, which returns without
     * touching the transport. Those rows are processed, not skipped, and they still cost the provider no connection.
     */
    @Test
    void aSliceWhoseRowsAllReachNoTransportOpensNothing() {

        var batches = new CountingBatches();
        var workers = poolOf((row, batch) -> Optional.empty(), 1);

        var outcomes = workers.runAll(ORGANIZATION_ID, rows(1L, 2L), batches::open, open());

        assertThat(outcomes).containsExactly(PROCESSED, PROCESSED);
        assertThat(batches.opened).hasValue(0);
    }

    /** Every row leaves the caller's thread; nothing is quietly run inline where it would hold up the whole batch. */
    @Test
    void runsEverySliceOnAThreadOfItsOwn() {

        var threads = new CopyOnWriteArrayList<Long>();
        var workers = poolOf((row, batch) -> {
            threads.add(Thread.currentThread().threadId());
            return Optional.empty();
        }, 3);

        workers.runAll(ORGANIZATION_ID, rows(1L, 2L, 3L), DrainWorkersTest::noOpBatch, open());

        assertThat(threads).hasSize(3).doesNotContain(Thread.currentThread().threadId()).doesNotHaveDuplicates();
    }

    /**
     * Slices are contiguous and concatenated in slice order, so the outcome at index i belongs to the row at index i
     * however the workers happened to interleave. Rows 3 and 5 fail, one in each of the two slices, and it is their own
     * two indexes that come back {@code FAILED}.
     */
    @Test
    void outcomesComeBackInTheOrderTheRowsWereGivenIn() {

        var workers = poolOf((row, batch) -> {
            if (row.getId() == 3L || row.getId() == 5L) {
                throw new IllegalStateException("boom");
            }
            return Optional.empty();
        }, 2);

        var outcomes = workers.runAll(ORGANIZATION_ID, rows(1L, 2L, 3L, 4L, 5L, 6L), DrainWorkersTest::noOpBatch,
                open());

        assertThat(outcomes).containsExactly(PROCESSED, PROCESSED, FAILED, PROCESSED, FAILED, PROCESSED);
    }

    /**
     * The case a single shared transaction could not survive, now asserted at the level that decides it: a genuine
     * {@link DataIntegrityViolationException} out of one row's own processing - a database error - is that row's
     * failure and nothing else's, and the next row of the same slice is still sent over the same connection.
     */
    @Test
    void aRowWhoseProcessingThrowsADataAccessExceptionDoesNotStopItsSlice() {

        var sent = new CopyOnWriteArrayList<Long>();
        var workers = poolOf((row, batch) -> {
            if (row.getId() == 1L) {
                throw new DataIntegrityViolationException("simulated database failure");
            }
            sent.add(row.getId());
            return Optional.empty();
        }, 1);

        var outcomes = workers.runAll(ORGANIZATION_ID, rows(1L, 2L), DrainWorkersTest::noOpBatch, open());

        assertThat(outcomes).containsExactly(FAILED, PROCESSED);
        assertThat(sent).containsExactly(2L);
    }

    /**
     * The write {@link OutboxRowProcessorChatTest} proves does NOT happen from inside
     * {@code OutboxRowProcessor.process} - see that class's own javadoc for why - happens here instead, on this
     * method's own stack rather than inside the row's rolled-back transaction. This is the one test in the whole plan
     * that can actually see the two on the same side: a {@link ChatSendException} out of the row work still marks that
     * one row {@code FAILED}, exactly like any other per-row failure, but it also reaches
     * {@link ChatDestinationsPort#recordFailure} - which no other exception shape in this class does.
     *
     * <p>
     * The row carries its own {@code chatDestinationId} - {@code 7L} - and that is what {@code recordFailure} is asked
     * about, with no {@code destinationsFor} resolution in between: see
     * {@link #aChatSendExceptionOnAPlaceholderRowRecordsNothingBecauseItHasNoDestination} for the other half of that
     * same guarantee, the placeholder that must record nothing at all.
     */
    @Test
    void aChatSendExceptionReportsTheFailureToChatDestinationsFromThisMethodsOwnStack() {

        var chatDestinations = mock(ChatDestinationsPort.class);
        var now = Instant.parse("2026-08-01T12:00:00Z");
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var row = OutboxEntity.builder().id(1L).organizationId(ORGANIZATION_ID).messageType("session_wrap_up")
                .subjectRef("41").channel("CHAT").chatDestinationId(7L).template("session-wrap-up").status("SENDING")
                .build();
        var workers = new DrainWorkers((r, batch) -> {
            throw new ChatSendException(ChatFailureKind.TRANSIENT, "503");
        }, 1, chatDestinations, clock);

        var outcomes = workers.runAll(ORGANIZATION_ID, List.of(row), DrainWorkersTest::noOpBatch, open());

        assertThat(outcomes).containsExactly(FAILED);
        verify(chatDestinations).recordFailure(eq(ORGANIZATION_ID), eq(7L),
                argThat(failure -> failure.kind() == ChatFailureKind.TRANSIENT && "503".equals(failure.error())
                        && now.equals(failure.at())));
    }

    /**
     * The other half of {@link #aChatSendExceptionReportsTheFailureToChatDestinationsFromThisMethodsOwnStack}'s
     * guarantee: a row with no {@code chatDestinationId} is the placeholder itself, which
     * {@link OutboxRowProcessor#expand} never hands to {@link ChatTransport} - so a real deploy cannot make this row
     * work throw {@link ChatSendException} at all. This test exercises the backstop anyway, proving
     * {@link DrainWorkers#recordChatFailure}'s null check answers it by recording nothing rather than guessing which
     * destination a failure with none of its own was meant for. The row is still reported {@code FAILED} either way -
     * only the health write is skipped.
     */
    @Test
    void aChatSendExceptionOnAPlaceholderRowRecordsNothingBecauseItHasNoDestination() {

        var chatDestinations = mock(ChatDestinationsPort.class);
        var row = OutboxEntity.builder().id(1L).organizationId(ORGANIZATION_ID).messageType("session_wrap_up")
                .subjectRef("41").channel("CHAT").template("session-wrap-up").status("SENDING").build();
        var workers = new DrainWorkers((r, batch) -> {
            throw new ChatSendException(ChatFailureKind.TRANSIENT, "503");
        }, 1, chatDestinations, Clock.systemUTC());

        var outcomes = workers.runAll(ORGANIZATION_ID, List.of(row), DrainWorkersTest::noOpBatch, open());

        assertThat(outcomes).containsExactly(FAILED);
        verifyNoInteractions(chatDestinations);
    }

    /**
     * {@code recordFailure} itself throwing - a genuine {@code DataAccessException}, not the poison-pill case
     * {@code ChatDestinationsAdapter} already defuses on its own side - must not also take the row's own
     * {@code Outcome.FAILED} down with it: every way a slice can end is answered inside the worker rather than by
     * letting anything escape (this class's own class javadoc), and {@code outcomeOf} is where that promise is kept.
     * Without the inner {@code try}/{@code catch} this row would propagate out of {@code runAll} entirely, and the
     * whole claimed batch behind it would sit in {@code SENDING} until its lease expired.
     */
    @Test
    void aChatDestinationsPortThatCannotRecordTheFailureStillReportsTheRowFailed() {

        var chatDestinations = mock(ChatDestinationsPort.class);
        doThrow(new DataIntegrityViolationException("simulated database failure")).when(chatDestinations)
                .recordFailure(any(), anyLong(), any());
        var row = OutboxEntity.builder().id(1L).organizationId(ORGANIZATION_ID).messageType("session_wrap_up")
                .subjectRef("41").channel("CHAT").chatDestinationId(7L).template("session-wrap-up").status("SENDING")
                .build();
        var workers = new DrainWorkers((r, batch) -> {
            throw new ChatSendException(ChatFailureKind.TRANSIENT, "503");
        }, 1, chatDestinations, Clock.systemUTC());

        var outcomes = workers.runAll(ORGANIZATION_ID, List.of(row), DrainWorkersTest::noOpBatch, open());

        assertThat(outcomes).containsExactly(FAILED);
    }

    /**
     * The success half of {@link #aChatSendExceptionReportsTheFailureToChatDestinationsFromThisMethodsOwnStack}: a row
     * work that returns the destination id it posted to - exactly what {@link OutboxRowProcessor#process} now returns
     * for a delivered chat row, having moved the write here for the transactional reasons its own javadoc gives -
     * reaches {@link ChatDestinationsPort#recordDelivered} from this method's own stack, after {@code work.run} has
     * already returned. Fixed clock so the {@code at} argument is checkable, the same shape the failure test above
     * uses.
     */
    @Test
    void aReturnedDestinationIdRecordsDeliveryOnThisMethodsOwnStack() {

        var chatDestinations = mock(ChatDestinationsPort.class);
        var now = Instant.parse("2026-08-01T12:00:00Z");
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var row = OutboxEntity.builder().id(1L).organizationId(ORGANIZATION_ID).messageType("session_wrap_up")
                .subjectRef("41").channel("CHAT").chatDestinationId(7L).template("session-wrap-up").status("SENDING")
                .build();
        var workers = new DrainWorkers((r, batch) -> Optional.of(7L), 1, chatDestinations, clock);

        var outcomes = workers.runAll(ORGANIZATION_ID, List.of(row), DrainWorkersTest::noOpBatch, open());

        assertThat(outcomes).containsExactly(PROCESSED);
        verify(chatDestinations).recordDelivered(ORGANIZATION_ID, 7L, now);
    }

    /**
     * The delivered-write mirror of {@link #aChatDestinationsPortThatCannotRecordTheFailureStillReportsTheRowFailed}:
     * {@code recordDelivered} throwing - the same {@code OptimisticLockingFailureException} two concurrent deliveries
     * to one destination can genuinely produce, or any transient {@code DataAccessException} - must not take the row's
     * own {@code Outcome.PROCESSED} down with it, and must not propagate out of {@code runAll} at all. This is the test
     * the old, wrongly-placed guard inside {@code OutboxRowProcessor} could never have been proven by: that guard sat
     * inside the row's own transaction, where a caught exception does not stop Spring from marking the ambient
     * transaction rollback-only anyway (see {@code DrainWorkers.recordChatDelivered}'s own javadoc) - moving the write
     * here is what makes catching it here actually sufficient.
     */
    @Test
    void aChatDestinationsPortThatCannotRecordDeliveryStillReportsTheRowProcessed() {

        var chatDestinations = mock(ChatDestinationsPort.class);
        doThrow(new DataIntegrityViolationException("simulated database failure")).when(chatDestinations)
                .recordDelivered(any(), anyLong(), any());
        var row = OutboxEntity.builder().id(1L).organizationId(ORGANIZATION_ID).messageType("session_wrap_up")
                .subjectRef("41").channel("CHAT").chatDestinationId(7L).template("session-wrap-up").status("SENDING")
                .build();
        var workers = new DrainWorkers((r, batch) -> Optional.of(7L), 1, chatDestinations, Clock.systemUTC());

        var outcomes = workers.runAll(ORGANIZATION_ID, List.of(row), DrainWorkersTest::noOpBatch, open());

        assertThat(outcomes).containsExactly(PROCESSED);
    }

    /**
     * A connection nobody can open holds the mail and burns an attempt on nothing: the row that met it is
     * {@code ACCOUNT_REFUSED} and every mail row behind it {@code HELD}, all released unmarked, and no second
     * connection is tried. The count is what matters - one outcome per row held - because the caller maps outcomes back
     * by index. Nothing was accepted, so the drain holds its mail for the tick on this batch.
     */
    @Test
    void aSliceWhoseConnectionIsRefusedReportsOneOutcomePerRowItHeld() {

        var workers = poolOf((row, batch) -> {
            batch.send(MAIL);
            return Optional.empty();
        }, 1);

        var opened = new AtomicInteger();
        var window = open();

        var outcomes = workers.runAll(ORGANIZATION_ID, rows(1L, 2L, 3L), () -> {
            opened.incrementAndGet();
            throw new MailAccountRefusedException("the mail transport refused the connection", null);
        }, window);

        assertThat(outcomes).containsExactly(ACCOUNT_REFUSED, HELD, HELD);
        assertThat(opened).hasValue(1);
        assertThat(window.acceptance().anyAccepted()).isFalse();
    }

    /**
     * A server capped on concurrent connections refuses the one past its cap and takes the others: the refusal holds
     * the mail of the slice that met it and nothing else. Slice one is {@code [1 mail, 2 in-app]} and slice two
     * {@code [3 mail, 4 mail]}. Row 3 waits for row 2, which runs only after slice one's connection was refused, so the
     * refusal always comes first and slice two's connection is always the second one opened, the one the server takes.
     * A hold shared across slices would answer row 4 {@code HELD} here, a message the server would have taken, and
     * since the batch had mail accepted the drain must not hold its later claims either: slice two's worker reports the
     * acceptance on its own thread, and this thread, standing in for the drain's, reads it once {@code runAll}
     * returned.
     */
    @Test
    void aRefusedConnectionHoldsOnlyItsOwnSlicesMailWhileAnotherWorkersMailIsStillSent() {

        var refusalSeen = new CountDownLatch(1);
        var sent = new CopyOnWriteArrayList<Long>();
        var workers = poolOf((row, batch) -> {
            if (row.getId() == 2L) {
                refusalSeen.countDown();
                return Optional.empty();
            }
            if (row.getId() == 3L) {
                awaitWithin5Seconds(refusalSeen);
            }
            batch.send(MAIL);
            sent.add(row.getId());
            return Optional.empty();
        }, 2);
        var opened = new AtomicInteger();
        var window = open();

        var outcomes = workers.runAll(ORGANIZATION_ID,
                List.of(rowWithId(1L), inChannel(2L, DeliveryChannel.IN_APP), rowWithId(3L), rowWithId(4L)), () -> {
                    if (opened.incrementAndGet() == 1) {
                        throw new MailAccountRefusedException("too many concurrent connections", null);
                    }
                    return noOpBatch();
                }, window);

        assertThat(outcomes).containsExactly(ACCOUNT_REFUSED, PROCESSED, PROCESSED, PROCESSED);
        assertThat(sent).containsExactly(3L, 4L);
        assertThat(window.acceptance().anyAccepted()).isTrue();
    }

    /**
     * {@link MailDelivery#open} promises to refuse only as a {@link MailAccountRefusedException}, but a worker holding
     * rows must not depend on that promise: anything at all escaping the supplier is still answered the same way,
     * because nothing was sent either way and releasing the rows unmarked is the only answer that burns no attempt.
     */
    @Test
    void aSliceWhoseConnectionFailsInSomeOtherWayStillAnswersForEveryRow() {

        var workers = poolOf((row, batch) -> {
            batch.send(MAIL);
            return Optional.empty();
        }, 1);

        var outcomes = workers.runAll(ORGANIZATION_ID, rows(1L, 2L), () -> {
            throw new IllegalStateException("the transport factory is misconfigured");
        }, open());

        assertThat(outcomes).containsExactly(ACCOUNT_REFUSED, HELD);
    }

    /**
     * An account refusal mid-slice holds the mail where it stands. Every mail row behind it would be refused
     * identically, and attempting them would burn an attempt each toward {@code ABANDONED} for messages nothing ever
     * rejected.
     */
    @Test
    void anAccountRefusalHoldsTheMailRowsBehindIt() {

        var attempted = new CopyOnWriteArrayList<Long>();
        var workers = poolOf((row, batch) -> {
            attempted.add(row.getId());
            if (row.getId() == 1L) {
                throw new MailAccountRefusedException("daily quota exceeded", null);
            }
            return Optional.empty();
        }, 1);

        var outcomes = workers.runAll(ORGANIZATION_ID, rows(1L, 2L, 3L), DrainWorkersTest::noOpBatch, open());

        assertThat(outcomes).containsExactly(ACCOUNT_REFUSED, HELD, HELD);
        assertThat(attempted).containsExactly(1L);
    }

    /**
     * A refused mail server stops mail and nothing else. The in-app entry and the chat post queued behind the refused
     * mail row never touch the mail server, so they are processed, in order, while the mail row after them is held with
     * the first.
     */
    @Test
    void aMailRefusalLetsTheInAppEntryAndTheChatPostBehindItThrough() {

        var attempted = new CopyOnWriteArrayList<Long>();
        var workers = poolOf((row, batch) -> {
            attempted.add(row.getId());
            if (row.getChannel() == null) {
                throw new MailAccountRefusedException("the mail server refused the sending account", null);
            }
            return Optional.empty();
        }, 1);
        var window = open();

        var outcomes = workers.runAll(ORGANIZATION_ID, List.of(rowWithId(1L), inChannel(2L, DeliveryChannel.IN_APP),
                inChannel(3L, DeliveryChannel.CHAT), rowWithId(4L)), DrainWorkersTest::noOpBatch, window);

        assertThat(outcomes).containsExactly(ACCOUNT_REFUSED, PROCESSED, PROCESSED, HELD);
        assertThat(attempted).containsExactly(1L, 2L, 3L);
    }

    /**
     * A send window that has closed - the tick's own budget spent while a worker waited for a slot - is not a failure
     * of the row that met it. It is reported {@code NOT_ATTEMPTED}, so no attempt is burned, and it stops the slice,
     * because no row behind it can be sent on this tick either.
     */
    @Test
    void aClosedSendWindowLeavesTheWholeSliceUnattempted() {

        var attempted = new CopyOnWriteArrayList<Long>();
        var workers = poolOf((row, batch) -> {
            attempted.add(row.getId());
            batch.send(MAIL);
            return Optional.empty();
        }, 1);

        var outcomes = workers.runAll(ORGANIZATION_ID, rows(1L, 2L, 3L), DrainWorkersTest::noOpBatch,
                new DrainWorkers.SendWindow(() -> true, () -> false, new MailAcceptance()));

        assertThat(outcomes).containsExactly(NOT_ATTEMPTED, NOT_ATTEMPTED, NOT_ATTEMPTED);
        assertThat(attempted).containsExactly(1L);
    }

    /**
     * The seam a shutdown arrives through. It is asked once per row and, once it refuses, not asked again: the rest of
     * the slice is already decided, and a shutdown does not change its mind.
     */
    @Test
    void aClosedAdmissionGateLeavesTheRestOfTheSliceUnattemptedAndIsNotAskedAgain() {

        var asked = new AtomicInteger();
        var workers = poolOf((row, batch) -> Optional.empty(), 1);

        var outcomes = workers.runAll(ORGANIZATION_ID, rows(1L, 2L, 3L), DrainWorkersTest::noOpBatch,
                new DrainWorkers.SendWindow(() -> asked.incrementAndGet() == 1, () -> true, new MailAcceptance()));

        assertThat(outcomes).containsExactly(PROCESSED, NOT_ATTEMPTED, NOT_ATTEMPTED);
        assertThat(asked).hasValue(2);
    }

    /**
     * The connection is closed on the way out of a row that failed, not only out of one that succeeded: a connection
     * left open by an exception is one the provider holds until it reaps it, and the next tick opens another beside it.
     */
    @Test
    void theConnectionIsClosedEvenWhenARowFails() {

        var batches = new CountingBatches();
        var workers = poolOf((row, batch) -> {
            batch.send(MAIL);
            throw new IllegalStateException("boom");
        }, 1);

        workers.runAll(ORGANIZATION_ID, rows(1L), batches::open, open());

        assertThat(batches.closed).hasValue(1);
    }

    /**
     * The {@code @Autowired} constructor is what production actually calls - every other test in this class goes
     * through the four-argument constructor directly instead, so nothing else here proves this one correctly binds
     * {@link OutboxRowProcessor#process} as the row work, rather than something that merely compiles, or reads the
     * worker count from {@link CommunicationProperties#drainWorkers} rather than a hardcoded constant. Eight rows
     * against {@code drainWorkers}'s fixture value of four is what makes the second half checkable at all: it divides
     * evenly into exactly four two-row slices, so a pool hardcoded to any other size would use a different number of
     * worker threads than {@code properties} actually configured.
     */
    @Test
    void theAutowiredConstructorBindsTheRowProcessorAndTheConfiguredWorkerCount() {

        var rowProcessor = mock(OutboxRowProcessor.class);
        var properties = CommunicationPropertiesMother.any();
        var threads = new CopyOnWriteArrayList<Long>();
        doAnswer(invocation -> {
            threads.add(Thread.currentThread().threadId());
            return Optional.empty();
        }).when(rowProcessor).process(any(), any());
        var workers = new DrainWorkers(rowProcessor, properties, mock(ChatDestinationsPort.class), Clock.systemUTC());

        var outcomes = workers.runAll(ORGANIZATION_ID, rows(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L),
                DrainWorkersTest::noOpBatch, open());

        assertThat(outcomes).hasSize(8).containsOnly(PROCESSED);
        assertThat(threads).hasSize(8);
        assertThat(new HashSet<>(threads)).hasSize(properties.drainWorkers());
        verify(rowProcessor, times(8)).process(any(), any());
    }

    private static void awaitWithin5Seconds(CountDownLatch latch) {

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("the other worker never reached its in-app row");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static List<OutboxEntity> rows(Long... ids) {

        return Stream.of(ids).map(DrainWorkersTest::rowWithId).toList();
    }

    /**
     * {@code DrainWorkers}' four-argument constructor, with a fake chat destinations port and a real clock that no test
     * below needs to assert on: every row work in this class either never throws {@link ChatSendException}, or is
     * {@link #aChatSendExceptionReportsTheFailureToChatDestinationsFromThisMethodsOwnStack}, which names its own mocked
     * {@code chatDestinations} and fixed {@code clock} explicitly instead of going through this helper. A production
     * {@code DrainWorkers} cannot be built with fewer than four arguments - there is no shortcut constructor for tests
     * to fall back onto null collaborators the class would otherwise dereference for real.
     */
    private static DrainWorkers poolOf(DrainWorkers.RowWork work, int workerCount) {

        return new DrainWorkers(work, workerCount, new StubChatDestinationsPort(), Clock.systemUTC());
    }

    private static OutboxEntity rowWithId(long id) {

        return OutboxEntity.builder().id(id).organizationId(ORGANIZATION_ID).messageType("session_wrap_up")
                .template("session-wrap-up").status("SENDING").build();
    }

    private static OutboxEntity inChannel(long id, DeliveryChannel channel) {

        return OutboxEntity.builder().id(id).organizationId(ORGANIZATION_ID).messageType("session_wrap_up")
                .template("session-wrap-up").channel(channel.name()).status("SENDING").build();
    }

    private static MailBatch recordingBatch(List<MailBatch> opened, List<MailBatch> received) {

        var batch = new MailBatch() {

            @Override
            public void send(OutgoingMail mail) {

                received.add(this);
            }

            @Override
            public void close() {
            }
        };
        opened.add(batch);
        return batch;
    }

    private static MailBatch noOpBatch() {

        return new MailBatch() {

            @Override
            public void send(OutgoingMail mail) {
            }

            @Override
            public void close() {
            }
        };
    }
}
