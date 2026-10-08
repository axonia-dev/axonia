package dev.axonia.communication.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Claims repeatedly, under a wall-clock budget, hands each claim to a pool of workers, and records what they report.
 * Called once per organization per tick by the sweep; this class knows nothing about organizations or scheduling, only
 * about its own repeated claiming.
 *
 * <p>
 * A claimed batch's rows are no longer drained by this thread but by {@link DrainWorkers}, a small bounded pool whose
 * every worker enters the organization's scope for itself - read that class's javadoc before touching any of this, and
 * {@code DrainWorkerScopeTest} before touching the scope. What is left here is the bookkeeping only a thread with no
 * transaction of its own can safely do: marking the rows the workers reported failed, and releasing the ones they never
 * sent.
 *
 * <p>
 * A tick claims repeatedly until a claim comes back empty or {@code deadline} is reached, rather than exactly once. The
 * single-batch shape made the scheduler the throughput ceiling: {@code batchSize} rows per {@code drain-interval},
 * 1,200 an hour on the defaults, whatever the transport could actually manage. {@code batchSize} now means only how
 * many rows one claim takes, which is what it always said. {@code deadline} is computed once, for the whole tick, by
 * {@code CommunicationSweep.drainTick} and handed down through {@link SweepUnit#drain} - never recomputed here from
 * {@code drainBudget} - so it bounds the tick as a whole rather than this one organization's own share of it, and it is
 * checked only between batches, not mid-batch - see {@link CommunicationProperties}' own javadoc for the exact shape of
 * that guarantee.
 *
 * <p>
 * The gate runs before address resolution, not after: a row the gate is going to suppress must not first pay for a
 * directory lookup, and {@link SuppressionReason#NO_ADDRESS} only needs to be distinguishable from the gate's own
 * reasons, not computed before them - see {@link OutboxRowProcessor}, which now owns that logic.
 *
 * <p>
 * A missing template fails the row, not the batch: {@code MailRenderer#render}'s {@code TemplateInputException} is
 * unchecked and falls into the same per-row {@code catch (RuntimeException)} in {@link DrainWorkers} as a transport
 * failure, so one dead template name marks its own rows {@code FAILED} - eventually {@code ABANDONED} - rather than
 * ever stopping every other message in the batch.
 *
 * <p>
 * No part of a row's processing runs inside a transaction this class controls. {@link #claim} in {@link JdbcOutbox} and
 * {@link OutboxRowProcessor#process} each carry their own {@code @Transactional}, so this class's only job is
 * orchestration: hand the claim to {@link DrainWorkers}, and record whatever it reports back. That split is what makes
 * a database error in one row's processing - a {@code DataAccessException}, which Postgres answers by refusing every
 * further statement on that same connection - unable to also poison the {@link JdbcOutbox#markFailed} call meant to
 * record it: that row's transaction, and its connection, are already gone by the time {@link #markFailures} runs on
 * this thread, and {@code markFailed} opens a connection of its own. The drain commits per message; see
 * {@link SweepUnit}'s class javadoc for the full shape of why the previous single-transaction-per-tick design could not
 * make that guarantee, and {@link OutboxRowProcessor}'s own javadoc for why the split had to be a separate bean rather
 * than a private method here.
 *
 * <p>
 * This is also what {@link #onShutdown}'s own javadoc means by "a row cut mid-send is the one failure mode this design
 * cannot close, only shrink from every deploy to only a hard kill": a process killed mid-{@code send} leaves that one
 * row's transaction uncommitted, so its mark never lands and the next claim resends it - a genuine duplicate, the
 * accepted cost. What a hard kill can no longer do is take the REST of the batch down with it. Under the old
 * single-transaction-per-tick design a kill during row 7 of a batch of 20 rolled back the claim and every mark written
 * so far, so all 20 rows - the 6 already sent and durably delivered, not only the one genuinely in flight - were
 * claimable and resent on the very next tick. Each row now commits (or rolls back) on its own, so a kill during row 7
 * loses row 7 alone: rows 1 through 6 already committed their own {@code markSent} and stay {@code SENT}. What
 * concurrency changes about that, and only that, is the count: with {@code drainWorkers} rows genuinely in flight at
 * once, a hard kill loses at most {@code drainWorkers} messages rather than one. Still bounded, still a small constant,
 * and still nothing like a batch.
 *
 * <p>
 * Every mark call reads {@code clock.instant()} at the moment it happens, not once for the whole batch: {@code
 * markFailed}'s {@code updated_at} is what the retry backoff is measured from, and stamping every row in a slow batch
 * with the batch's start time would let a row that failed six minutes in look, to the next tick, like it failed at
 * minute zero - consuming the entire backoff before the row is even claimable again, in precisely the degraded-SMTP
 * case the backoff exists for.
 *
 * <p>
 * Both the jsonb-rehydration repair (see {@link FactRehydration}) and the footer link's fact key and path (see
 * {@link UnsubscribeLinks}) are extracted rather than held here, because {@link MessageRenderAdapter} - rendering a
 * stored row for a wrap-up preview once one already exists - needs the identical repair and the identical link, and a
 * second hand-copied version of either would let a preview silently render something a real send never would.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class OutboxDrain {

    /**
     * The three outcomes that mean the row was never sent and no attempt may be burned against it, so it goes straight
     * back to {@code PENDING} instead of waiting out its {@code claimLease}.
     */
    private static final Set<DrainWorkers.Outcome> UNSENT = Set.of(DrainWorkers.Outcome.ACCOUNT_REFUSED,
            DrainWorkers.Outcome.HELD, DrainWorkers.Outcome.NOT_ATTEMPTED);

    private final JdbcOutbox outbox;
    private final DrainWorkers workers;
    private final MailDelivery delivery;
    private final Clock clock;
    private final CommunicationProperties properties;
    private final SendPacing pacing;

    private final AtomicBoolean stopping = new AtomicBoolean();

    /**
     * Set by the context shutting down, not by anything in this class's own control flow. {@code drain} checks it
     * before claiming a new batch, every worker checks it through {@link #admits} before each of its own rows, and
     * {@link SendPacing#awaitSendSlot} reads it between its own waits, so a shutdown arriving while a worker is waiting
     * for a send slot is honoured as soon as that one wait ends rather than at the end of the budget. Rows already in
     * flight finish rather than being cut off - see the class javadoc on why a row cut mid-send is the one failure mode
     * this design cannot close, only shrink from "every deploy" to "only a hard kill".
     */
    @EventListener(ContextClosedEvent.class)
    void onShutdown() {

        stopping.set(true);
    }

    /**
     * One organization's share of a tick. Its {@link MailHold} lives exactly as long as this call: once a batch meets a
     * refusal with no mail accepted, the claims that follow leave its mail rows where they are and take only its in-app
     * entries and chat posts, so those flow however long the refusal lasts, and the next tick tries the mail again.
     */
    void drain(Instant deadline) {

        var mail = new MailHold();
        while (!stopping.get() && clock.instant().isBefore(deadline)) {
            var claimed = claimBatch(mail);
            if (claimed.isEmpty()) {
                return;
            }
            if (!processBatch(claimed, sendWindow(deadline), mail)) {
                break;
            }
        }
        reportBudgetSpent(deadline);
    }

    /**
     * The one operator-visible signal that a tenant is falling behind, and it must fire however the budget ran out.
     * Ending the loop on its own guard is the rare case now: at the vendor's ceiling nearly all of a tick's elapsed
     * time is spent inside a worker's wait for a send slot, so the deadline lands mid-batch far more often than between
     * two of them - reported here, on the way out, rather than only where the loop guard happens to catch it.
     *
     * <p>
     * Silent on a shutdown, which is not a backlog, and silent where a batch was cut short before the deadline by a
     * shutdown between rows, because rows released with the budget still unspent say nothing about how far behind this
     * organization is.
     */
    private void reportBudgetSpent(Instant deadline) {

        if (!stopping.get() && !clock.instant().isBefore(deadline)) {
            log.info("Outbox drain budget of {} spent with rows possibly still queued; resuming next tick",
                    properties.drainBudget());
        }
    }

    private List<OutboxEntity> claimBatch(MailHold mail) {

        return outbox.claim(JdbcOutbox.ClaimQuery.builder().batchSize(properties.batchSize()).now(clock.instant())
                .maxAttempts(properties.maxAttempts()).retryBackoff(properties.retryBackoff())
                .claimLease(properties.claimLease()).mailHeld(mail.isHeld()).build());
    }

    /**
     * False when the batch was cut short, by a shutdown or by the tick's own deadline arriving while a worker waited
     * for a send slot. A refusal by the mail server does not cut it short: the refused rows go back unmarked, and the
     * drain goes on claiming, leaving the mail out once a batch had none of it accepted.
     *
     * <p>
     * The whole claim goes to {@link DrainWorkers} in one call and comes back as one outcome per row, in the order the
     * rows were given. This thread then does the two things a worker deliberately does not: it marks the rows the
     * workers reported failed, and it releases the ones they never sent. Both belong here because both open a
     * connection of their own - {@link JdbcOutbox#markFailed} in particular must run on a connection that was never
     * party to whatever went wrong inside the worker.
     *
     * <p>
     * The release runs before the marking, and that order is load-bearing. {@link #markFailures} is deliberately
     * unguarded - a failure recording a failure must surface - but it shares a method with the release. Marking first
     * would let one transient {@code DataAccessException} out of {@code markFailed} also skip the release of every
     * {@code NOT_ATTEMPTED} row behind it, stranding rows a shutdown had already decided to hand back until their
     * {@code claimLease} expired. Releasing first makes the two independent again, for free.
     *
     * <p>
     * Nothing here rechecks {@code stopping}: a batch that completed returns true, and {@link #drain}'s own loop guard
     * is what refuses to claim another one on the way round.
     *
     * <p>
     * The mail server hears how the batch went first, on this thread and outside every row's transaction, so neither a
     * rolled-back row, nor a {@link #markFailures} that throws on purpose, nor a worker whose failure escapes
     * {@code runAll} can lose the report or leave it for the next batch to report as its own.
     */
    private boolean processBatch(List<OutboxEntity> claimed, DrainWorkers.SendWindow window, MailHold mail) {

        List<DrainWorkers.Outcome> outcomes;
        try {
            outcomes = workers.runAll(claimed.getFirst().getOrganizationId(), claimed, delivery::open, window);
        } finally {
            delivery.reportOutcome();
        }
        mail.endBatch(outcomes.contains(DrainWorkers.Outcome.ACCOUNT_REFUSED), window.acceptance().anyAccepted());
        if (mail.firstRefusal()) {
            log.warn("The mail server refused this organization's mail (the connection, the sender, an account limit "
                    + "or a dropped connection); handing the refused mail back unmarked, its other rows go on");
        }
        var unsent = rowsWith(claimed, outcomes, UNSENT::contains);
        if (!unsent.isEmpty()) {
            releaseRemaining(unsent);
        }
        markFailures(claimed, outcomes);
        return !outcomes.contains(DrainWorkers.Outcome.NOT_ATTEMPTED);
    }

    /**
     * The state of this tick, as a worker sees it: whether it should still be draining at all, and how it buys the
     * right to hand one message to the transport.
     *
     * <p>
     * {@code deadline} is handed down rather than read again from the clock inside the worker, so the budget computed
     * once in {@link #drain} stays the single authority on when a tick ends: a worker waiting for a send slot cannot
     * outlast it, and cannot disagree with it either.
     */
    private DrainWorkers.SendWindow sendWindow(Instant deadline) {

        return new DrainWorkers.SendWindow(this::admits, () -> pacing.awaitSendSlot(deadline, stopping::get),
                new MailAcceptance());
    }

    /**
     * Asked by a worker before each of its own rows, never once for the batch, and it now carries the shutdown flag
     * alone. The send pace used to live here too, which meant a slot was spent on every row rather than on every
     * message - a suppressed row costs no slot now - and, worse, that a merely-paced refusal ended the tick and left
     * the rest of the queue waiting a whole {@code drain-interval}. Pacing is a wait inside {@link PacedMailBatch}, not
     * a verdict here; see {@link SendPacing}'s own javadoc for why those are opposite things.
     */
    private boolean admits() {

        return !stopping.get();
    }

    /**
     * Every mark reads {@code clock.instant()} at the moment it happens rather than once for the batch, for the reason
     * the class javadoc gives: {@code markFailed}'s {@code updated_at} is what the retry backoff is measured from.
     */
    private void markFailures(List<OutboxEntity> claimed, List<DrainWorkers.Outcome> outcomes) {

        rowsWith(claimed, outcomes, DrainWorkers.Outcome.FAILED::equals).forEach(row -> outbox.markFailed(row.getId(),
                row.getOrganizationId(), clock.instant(), properties.maxAttempts()));
    }

    /** Outcomes come back one per row, in the order the rows were handed over, so the index is the row. */
    private static List<OutboxEntity> rowsWith(List<OutboxEntity> claimed, List<DrainWorkers.Outcome> outcomes,
            Predicate<DrainWorkers.Outcome> wanted) {

        return IntStream.range(0, outcomes.size()).filter(i -> wanted.test(outcomes.get(i))).mapToObj(claimed::get)
                .toList();
    }

    /**
     * Best-effort: a row left {@code SENDING} here is not lost, only delayed until {@code claimLease} expires and a
     * later tick reclaims it (see {@link JdbcOutbox#claim}). Releasing it back to {@code PENDING} immediately - on the
     * way out of a graceful shutdown, or the moment a {@link MailAccountRefusedException} holds a tick's mail - is what
     * turns either case into something free rather than merely survivable, or merely delayed by a lease. A failure here
     * must never turn either case into a crash, so it is caught and logged, not rethrown - the worst case is the row
     * falling back to the ordinary lease-expiry path this already tolerates. {@code remaining} is never empty at this
     * class's one call site, which only reaches here once it has found a row to release, but a stray empty call is
     * still safe: {@code getFirst()} on an empty list raises unchecked, which the same catch below already handles.
     */
    private void releaseRemaining(List<OutboxEntity> remaining) {

        try {
            var organizationId = remaining.getFirst().getOrganizationId();
            var ids = remaining.stream().map(OutboxEntity::getId).toList();
            outbox.release(ids, organizationId, clock.instant());
        } catch (RuntimeException e) {
            log.warn("Releasing {} claimed rows failed: {}", remaining.size(), e.getClass().getSimpleName());
        }
    }

}
