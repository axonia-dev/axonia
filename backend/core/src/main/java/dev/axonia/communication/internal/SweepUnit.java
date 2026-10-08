package dev.axonia.communication.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.communication.MessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * One organization's worth of work for one tick of the produce, drain or retention schedule (produce and drain on their
 * own cadences, plus the retention pass): {@link #produce} enqueues whatever is due, {@link #drain} sends whatever is
 * now claimable, {@link #retain} expires whatever has aged out. What used to be one {@code run} method calling both
 * produce and drain in sequence is split into these two, because {@link CommunicationSweep} now calls them from two
 * independent {@code @Scheduled} methods rather than one - producing is expensive and rarely urgent, draining is cheap
 * and time-sensitive, and tying them to the same interval meant every tick paid the expensive half's cost to get the
 * cheap half's latency.
 *
 * <p>
 * Neither method carries a {@code @Transactional}: no single transaction spans both halves of a tick, and splitting the
 * schedule removes even the "both halves" framing - each method is now the entire transactional unit dispatched by its
 * own {@code OrganizationScope.runAs} wrap. This method opens none on a producer's behalf - a producer that needs one
 * opens its own, and every {@code runAs} wrap therefore still sits outside it rather than inside, which is what lets
 * {@code app.current_org} be chosen on the connection that transaction acquires. The producers that open none need
 * none: the only writes such a producer performs are through {@link dev.axonia.communication.CommunicationPort#enqueue}
 * ({@code JdbcOutbox.enqueue}, already {@code @Transactional} on that method directly), and the
 * {@code ON CONFLICT DO NOTHING} unique key that backs idempotency makes each producer's own inserts safe to commit
 * independently: a kill between two producers, or between two rows one producer enqueues, leaves nothing to roll back
 * that the next tick cannot simply re-derive and re-attempt. {@code drain.drain(deadline)} itself owns two further
 * transaction boundaries of its own: {@link JdbcOutbox#claim} claims the batch in one, and each row - through
 * {@link OutboxRowProcessor}, a bean {@code drain} calls, never a private method inside it - is processed in another.
 * See {@link OutboxDrain}'s and {@link JdbcOutbox}'s own class javadoc for why splitting those two apart is what turns
 * the duplicate-send window back into one message rather than one batch, and what stops a single database error from
 * poisoning every row that has not been reached yet.
 *
 * <p>
 * None of that changes what still makes this a distinct Spring bean rather than a pair of methods on
 * {@link CommunicationSweep} itself: {@code OrganizationScope.runAs} wraps each call into {@code produce} or
 * {@code drain} from outside, and every transaction either method's callees open - {@code enqueue}'s, {@code claim}'s,
 * {@code OutboxRowProcessor.process}'s - acquires its own connection while that scope is still open on the same thread,
 * which is what lets {@code OrganizationAwareDataSource} set {@code app.current_org} on each of them in turn. A
 * self-invoked {@code @Transactional} method would have bypassed the proxy entirely - the same trap
 * {@code OrganizationScope}'s own javadoc warns about - but the fix here is structural even without one: nothing this
 * class calls is ever called through {@code this}.
 *
 * <p>
 * {@code organizationId} is not used to build any query here: every query a producer or {@link OutboxDrain#drain} runs
 * relies on the {@code app.current_org} GUC that {@code OrganizationScope.runAs} already set on the caller's
 * {@code SecurityContext} before any of those queries' own connections were acquired. It stays a parameter anyway - the
 * caller already has it, passing it costs nothing, and it is what makes this class's own unit tests legible.
 *
 * <p>
 * The two schedules can and do overlap in production: Boot's auto-configured {@code TaskScheduler} is a
 * {@code SimpleAsyncTaskScheduler} here (see {@code SchedulingShutdownConfiguredTest}'s javadoc for why -
 * {@code spring.threads.virtual.enabled=true} routes {@code TaskSchedulingAutoConfiguration} to that implementation
 * rather than a fixed-size {@code ThreadPoolTaskScheduler}), and that scheduler runs every invocation on its own fresh
 * (virtual) thread rather than a shared pool. A produce tick that runs long therefore never starves a drain tick that
 * comes due while it is still running; the two run concurrently on separate threads, each with its own call stack
 * through this class. That concurrency is also why {@code OrganizationScope.runAs} needs no locking of its own: it
 * mutates {@code SecurityContextHolder}, whose default strategy is thread-local, so a produce tick's scope for one
 * organization and a drain tick's scope for another never see each other's {@code SecurityContext}, even when both are
 * open at the same instant.
 *
 * <p>
 * {@link MessageProducer} is injected as a list, not as one bean: more than one module produces messages now
 * ({@code events}' session notices, {@code roundup}'s quarterly letter), and each is a separate implementation of the
 * same port precisely so this class never learns what any of them is about. Each gets its own {@code try/catch}, the
 * same isolation {@link CommunicationSweep} already gives each organization and for the same reason: one producer's bad
 * quarter, missing template or transient failure must never cost every producer after it its tick, and the order Spring
 * happens to inject them in must not decide who produces. The log carries the organization id and the failing
 * exception's class name only, never anything about a message. All of them are handed the SAME instant, read once
 * before the loop: a tick is one moment, and a producer that computed its own would be able to land two messages from
 * the same tick in different periods.
 *
 * <p>
 * {@link #retain} is the third tick's whole unit of work (the ninety-day pass): one call into
 * {@link OutboxRetention#expire}, computing {@code now} once from the same injected {@code Clock} {@link #produce}
 * uses, and deriving {@code cutoff} from it as {@code now.minus(properties.outboxRetention())} rather than letting
 * {@link OutboxRetention} read the clock itself. {@code now} and {@code cutoff} are passed as two distinct instants
 * because they mean different things - see {@link OutboxRetention}'s own javadoc - and {@code CommunicationSweep} wraps
 * this call in {@code OrganizationScope.runAs} exactly like the other two.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class SweepUnit {

    private final List<MessageProducer> messageProducers;
    private final OutboxDrain drain;
    private final Clock clock;
    private final OutboxRetention retention;
    private final CommunicationProperties properties;

    void produce(String organizationId) {

        var now = clock.instant();
        for (var producer : messageProducers) {
            try {
                producer.produce(now);
            } catch (RuntimeException e) {
                log.warn("Message production failed for organization {}: {}", organizationId,
                        e.getClass().getSimpleName());
            }
        }
    }

    void drain(String organizationId, Instant deadline) {

        drain.drain(deadline);
    }

    void retain(String organizationId) {

        var now = clock.instant();
        retention.expire(now, now.minus(properties.outboxRetention()), properties.retentionBatchSize());
    }
}
