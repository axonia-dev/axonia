package dev.axonia.communication.internal;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.axonia.OrganizationScope;
import dev.axonia.communication.ChatDestinationsPort;
import dev.axonia.communication.ChatDestinationsPort.ChatFailure;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a claimed batch's rows on a bounded pool, one transaction each, with every worker entering the organization's
 * scope for itself.
 *
 * <p>
 * <b>The scope is the whole reason this class exists rather than a bare executor.</b> {@link OrganizationScope} stores
 * the acting organization in {@code SecurityContextHolder}, whose default strategy is thread-local and not inheritable,
 * and {@code app.current_org} is chosen when a transaction acquires its connection. A worker that inherited nothing
 * would open its transaction unscoped, and Row-Level Security answers an unscoped query with zero rows rather than an
 * error: the row would silently read as already handled instead of failing loudly. Every worker therefore wraps its own
 * slice, and {@code DrainWorkerScopeTest} is what stops that regressing - it is the only thing that can see it.
 *
 * <p>
 * The pool is small and its size is bound configuration, and it is sized against two ceilings at once: each in-flight
 * row holds a database connection for the whole duration of its send, so the worker count has to leave the primary
 * Hikari pool enough connections to keep serving HTTP requests, and each worker also holds one SMTP connection to the
 * provider, which providers cap per account independently of the message rate. See {@code application.properties}'
 * comment on {@code drain-workers} for the arithmetic behind the shipped value.
 *
 * <p>
 * Concurrency widens exactly one window this module already documents. {@link OutboxDrain}'s class javadoc calls a
 * process killed mid-send "one message, not one batch"; with {@code drainWorkers} rows genuinely in flight at once it
 * is at most {@code drainWorkers} messages, still bounded and still far short of a batch. Nothing else about the
 * per-row guarantees moves: {@link OutboxRowProcessor} still owns one transaction per row and still writes
 * {@code markSent} before that row's own send.
 */
@Component
@Slf4j
class DrainWorkers {

    /**
     * {@code NOT_ATTEMPTED} is not a failure and must never be recorded as one: it names a row nothing was ever sent
     * for, because the tick was told to stop - a shutdown between rows, a tick whose deadline ran out while a worker
     * waited for a send slot, or a slice that had already been stopped. The caller releases those rows straight back to
     * {@code PENDING}, which is what keeps a graceful shutdown and a closed send window free rather than merely
     * survivable. {@code ACCOUNT_REFUSED} is released the same way and for the same reason - the row was never sent, so
     * no attempt may be burned against it - but it is a distinct outcome because it, and only it, means the mail server
     * refused. {@code HELD} is a mail row met after that refusal in the same slice, released unsent without being
     * touched.
     */
    enum Outcome {
        PROCESSED, FAILED, ACCOUNT_REFUSED, HELD, NOT_ATTEMPTED
    }

    /**
     * The two questions a worker asks the tick it belongs to. {@code draining} is read once per row, before the row is
     * touched at all, and answers false once the context is shutting down. {@code sendSlot} is read once per message
     * actually being handed to the transport, waits where the vendor's pace is momentarily spent, and answers false
     * only once the tick's own window has closed.
     *
     * <p>
     * {@code acceptance} is this batch's {@link MailAcceptance}: every worker tells it of each message the mail server
     * accepted, which is what lets the drain tell a server refusing one connection from one refusing them all.
     *
     * <p>
     * One parameter rather than three because they are one thing - the state of the tick these rows belong to - and
     * because they must come from the same tick: a worker pacing against one tick's deadline while another tick's stop
     * flag governs it would be a bug nothing else could catch.
     */
    record SendWindow(BooleanSupplier draining, BooleanSupplier sendSlot, MailAcceptance acceptance) {
    }

    /**
     * What one row's processing is, seen from here. Deliberately a lambda seam rather than an
     * {@link OutboxRowProcessor} field: it lets {@code DrainWorkerScopeTest} assert what a worker thread can see with
     * no mock, no transaction and no database in the way.
     *
     * <p>
     * The return value is the destination id a chat post actually reached, present only when this call posted for real
     * - never for a mail row, a suppressed row, or a placeholder's expansion. It is how {@link #outcomeOf} knows
     * whether to call {@link #recordChatDelivered}, and it exists for exactly the reason {@link #recordChatDelivered}'s
     * own javadoc gives: the write cannot be made from inside the row's own transaction, so the one thing that method
     * can safely carry out of it is this value, not a side effect.
     */
    @FunctionalInterface
    interface RowWork {

        Optional<Long> run(OutboxEntity row, MailBatch batch);
    }

    private final RowWork work;
    private final int workers;
    private final ChatDestinationsPort chatDestinations;
    private final Clock clock;

    @Autowired
    DrainWorkers(OutboxRowProcessor rowProcessor, CommunicationProperties properties,
            ChatDestinationsPort chatDestinations, Clock clock) {

        this(rowProcessor::process, properties.drainWorkers(), chatDestinations, clock);
    }

    DrainWorkers(RowWork work, int workers, ChatDestinationsPort chatDestinations, Clock clock) {

        this.work = work;
        this.workers = workers;
        this.chatDestinations = chatDestinations;
        this.clock = clock;
    }

    /**
     * Rows are split into contiguous slices, one per worker, rather than one task per row. That is what lets a worker
     * open one mail connection and send every row of its slice through it: a task per row would open and close a
     * connection per message, undoing the whole point of the batch. Contiguous rather than round-robin so concatenating
     * the slices' outcomes reproduces the input order, which is what the caller indexes back against its rows.
     *
     * <p>
     * The returned list always has exactly one outcome per row given. Every way a slice can end - a refused connection,
     * a row that threw, a tick told to stop - is answered inside the worker with a full-length answer for the rows it
     * was holding, rather than by letting anything escape: a list that came back short would silently shift every
     * outcome after it onto the wrong row in the caller's index-based bookkeeping, marking one row failed for another
     * row's failure.
     *
     * <p>
     * {@code window.draining()} is asked once per row, on the worker, immediately before that row is processed - never
     * once for the batch. Asking it per row is what makes a shutdown stop between rows rather than only between
     * batches. Once it refuses, the rest of that slice is left {@code NOT_ATTEMPTED} and it is not asked again. The
     * send pace is deliberately not part of that question: a paced refusal is a wait of the order of a tenth of a
     * second, taken inside {@link PacedMailBatch#send}, and answering it here instead would end the tick on it - see
     * {@link SendPacing}'s javadoc for why those are opposite things.
     */
    List<Outcome> runAll(String organizationId, List<OutboxEntity> rows, Supplier<MailBatch> batches,
            SendWindow window) {

        if (rows.isEmpty()) {
            return List.of();
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var running = slice(rows).stream().map(slice -> CompletableFuture
                    .supplyAsync(() -> runSlice(organizationId, slice, batches, window), executor)).toList();
            return running.stream().flatMap(slice -> slice.join().stream()).toList();
        }
    }

    /**
     * At most one slice per worker, and never an empty one: fewer rows than workers means fewer slices, not idle
     * workers each opening a connection to send nothing.
     */
    private List<List<OutboxEntity>> slice(List<OutboxEntity> rows) {

        var size = Math.ceilDiv(rows.size(), workers);
        return IntStream.range(0, Math.ceilDiv(rows.size(), size))
                .mapToObj(i -> rows.subList(i * size, Math.min((i + 1) * size, rows.size()))).toList();
    }

    /**
     * One worker: one organization scope, one mail connection at most, its slice's rows in order.
     *
     * <p>
     * {@link OrganizationScope#runAs} wraps the batch handle as well as the rows, because it must wrap the
     * transactional entry point rather than sit inside one, and because a connection opened by anything tenant-scoped
     * would otherwise be the single call in a worker's life running unscoped.
     *
     * <p>
     * What is handed down is the means to open a connection, never an already-open one: a JavaMail {@code Transport} is
     * not thread-safe, so each worker needs its own, which is what pays the connect, its TLS negotiation and its
     * authentication once per worker rather than once per row. And it is not opened here either. {@link PacedMailBatch}
     * opens it on the first message that actually has a send slot, so a slice that sends nothing - a shutdown, a slice
     * of rows the gate suppresses, a tick whose window closed - costs the provider no connection at all.
     */
    private List<Outcome> runSlice(String organizationId, List<OutboxEntity> slice, Supplier<MailBatch> batches,
            SendWindow window) {

        return OrganizationScope.runAs(organizationId, () -> {
            try (var batch = new PacedMailBatch(batches, window.sendSlot(), window.acceptance()::accepted)) {
                return sendAll(slice, batch, window);
            }
        });
    }

    /**
     * A refusal by the mail server stops this slice's mail and nothing else. Every mail row after it in this slice is
     * answered {@code HELD} without being touched: each would meet the same refused connection or sender, and burning
     * an attempt on each is precisely the attrition toward {@code ABANDONED} the refusal path exists to prevent. The
     * in-app entries and chat posts among them never touch the mail server, so they go on, in order. Other slices keep
     * their own connections: a server that refuses one connection past its cap on concurrent ones still takes the mail
     * of the others, and whether to stop claiming mail at all is the drain's call, through {@link MailHold}.
     *
     * <p>
     * A closed send window does stop the slice: the tick's own deadline has passed and no row behind this one can be
     * sent either.
     */
    private List<Outcome> sendAll(List<OutboxEntity> slice, MailBatch batch, SendWindow window) {

        var outcomes = new ArrayList<Outcome>(slice.size());
        var mailHeld = false;
        for (var row : slice) {
            if (!window.draining().getAsBoolean()) {
                break;
            }
            var outcome = mailHeld && DeliveryChannel.isMail(row.getChannel()) ? Outcome.HELD : outcomeOf(row, batch);
            mailHeld |= outcome == Outcome.ACCOUNT_REFUSED;
            outcomes.add(outcome);
            if (outcome == Outcome.NOT_ATTEMPTED) {
                break;
            }
        }
        outcomes.addAll(Collections.nCopies(slice.size() - outcomes.size(), Outcome.NOT_ATTEMPTED));
        return outcomes;
    }

    /**
     * A refusal is reported, never logged here: it holds this slice's mail but it is a property of the tick, which is
     * where the one warning line about it belongs - {@code drainWorkers} workers each logging the same provider refusal
     * would say the same thing that many times over. A closed send window is reported the same way and logged nowhere
     * here for the same reason: it is a property of the tick, and {@code OutboxDrain} reports the spent budget once, on
     * its way out. An ordinary per-row failure is the opposite: it names one row, and this is the only place that still
     * holds the exception it names.
     *
     * <p>
     * {@link ChatSendException} is caught here rather than left to fall into the generic {@link RuntimeException} arm
     * below for one reason: {@link ChatDestinationsPort#recordFailure} must run on this method's own stack, never
     * inside {@link OutboxRowProcessor#process}'s transaction. That row's {@code markSent} rolls back on this exact
     * failure, and a health write made from inside that transaction would roll back with it - the consecutive-failure
     * count the broken-channel rule reads would never advance, and a dead webhook would retry forever instead of ever
     * being declared broken.
     *
     * <p>
     * The success path answers the identical question in the other direction: {@link RowWork#run}'s return value names
     * the destination a post actually reached, and {@link #recordChatDelivered} runs here, after {@code work.run} has
     * already returned - which means after {@link OutboxRowProcessor#process}'s transaction has already committed, not
     * merely returned control. See {@link #recordChatDelivered}'s own javadoc for why that distinction is load-bearing.
     */
    private Outcome outcomeOf(OutboxEntity row, MailBatch batch) {

        try {
            var delivered = work.run(row, batch);
            delivered.ifPresent(destinationId -> recordChatDelivered(row, destinationId));
            return Outcome.PROCESSED;
        } catch (SendWindowClosedException closed) {
            return Outcome.NOT_ATTEMPTED;
        } catch (MailAccountRefusedException refused) {
            return Outcome.ACCOUNT_REFUSED;
        } catch (ChatSendException chatFailure) {
            recordChatFailure(row, chatFailure);
            return Outcome.FAILED;
        } catch (RuntimeException e) {
            log.warn("Outbox row {} ({} / {}) failed to send: {}", row.getId(), row.getMessageType(), row.getTemplate(),
                    e.getClass().getSimpleName());
            return Outcome.FAILED;
        }
    }

    /**
     * Best-effort, the same shape {@code OutboxDrain#releaseRemaining} already uses: every way a slice can end is
     * answered inside the worker rather than by letting anything escape (see this class's own class javadoc), and a
     * {@link ChatDestinationsPort#recordFailure} that itself throws - a genuine {@code DataAccessException}, not the
     * poison-pill case {@code ChatDestinationsAdapter} already defuses - must not take {@link #outcomeOf}'s own
     * {@code Outcome.FAILED} down with it. The row is reported failed and retried either way; only the health write is
     * at risk here.
     *
     * <p>
     * Records against {@code row.getChatDestinationId()} directly, never re-resolved through
     * {@link ChatDestinationsPort#destinationsFor}: an expanded row already names the one destination it posted to, and
     * re-resolving instead of trusting that would let a second destination's failure advance a first destination's
     * streak - a healthy destination declared broken while the one actually failing never is. That is precisely the
     * per-destination health this whole feature depends on, and it is only genuinely per-destination if this write
     * names the row's own destination and no other.
     *
     * <p>
     * A {@code null} id means {@code row} is the placeholder itself. {@link OutboxRowProcessor#expand} never hands a
     * placeholder to {@link ChatTransport}, so a real {@link ChatSendException} can never legitimately carry one here -
     * this check is a backstop against that invariant breaking, not a path production data reaches, and it records
     * nothing rather than guessing which destination a failure with no destination of its own was meant for.
     */
    private void recordChatFailure(OutboxEntity row, ChatSendException chatFailure) {

        var destinationId = row.getChatDestinationId();
        if (destinationId == null) {
            return;
        }
        try {
            chatDestinations.recordFailure(row.getOrganizationId(), destinationId,
                    new ChatFailure(chatFailure.kind(), chatFailure.getMessage(), clock.instant()));
        } catch (RuntimeException recordingFailed) {
            log.warn("Recording the chat destination failure for outbox row {} failed: {}", row.getId(),
                    recordingFailed.getClass().getSimpleName());
        }
    }

    /**
     * The success half of {@link #recordChatFailure}, and the reason this write cannot be made from inside
     * {@link OutboxRowProcessor#sendToDestination} the way it reads as though it should be: {@code
     * ChatDestinationsPort#recordDelivered} is ultimately backed by {@code ChatHealthRecorder.recordDelivered}, which
     * is itself {@code @Transactional} and reached through a real cross-bean proxy - a genuine PARTICIPATING
     * transaction under {@link OutboxRowProcessor#process}'s own {@code REQUIRED} boundary, not a nested one of its
     * own. Spring's {@code AbstractPlatformTransactionManager} marks a participating transaction's ambient transaction
     * rollback-only the moment that inner call throws, {@code globalRollbackOnParticipationFailure} being {@code true}
     * by default - catching the exception one frame up, inside {@code process()} itself, does not undo that mark. The
     * row's own {@code markSent} would still roll back at commit, now behind an {@code UnexpectedRollbackException}
     * instead of the original one, which is strictly worse: the same duplicate post this whole guard exists to prevent,
     * plus a misleading exception on the way out. Calling it here instead - after {@code work.run} has returned,
     * meaning after {@code process()}'s transaction has already committed - is what actually gives this write the
     * property {@link #recordChatFailure} already has: its own failure can only ever cost {@code
     * last_posted_at} and the failure-count reset, never the row whose post already left.
     */
    private void recordChatDelivered(OutboxEntity row, long destinationId) {

        try {
            chatDestinations.recordDelivered(row.getOrganizationId(), destinationId, clock.instant());
        } catch (RuntimeException recordingFailed) {
            log.warn("Recording a delivered chat post for destination {} failed: {}", destinationId,
                    recordingFailed.getClass().getSimpleName());
        }
    }
}
