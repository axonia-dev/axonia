package dev.axonia.communication.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.axonia.RateLimiter;
import dev.axonia.RateLimiters;

/**
 * How fast the drain is allowed to hand messages to the transport, so that concurrency is responsible rather than
 * reckless: the ceiling is the vendor's documented one, not however many worker threads happen to exist.
 *
 * <p>
 * One global key, not one per organization. The provider's limit is per account, and every tenant's mail leaves through
 * the same account, so a per-tenant budget would let two busy tenants together exceed a limit neither breached alone.
 *
 * <p>
 * The limiter's state is shared through Redis rather than held in this JVM, which is the property that matters on a
 * platform running more than one instance: two replicas each pacing themselves at the ceiling would together sit at
 * twice it. Its degraded mode - shared state unreachable, so the call is allowed but counted and logged - is inherited
 * deliberately: a paused mail queue is a worse outcome than a brief overshoot the operator can see in the counters.
 *
 * <p>
 * <b>A refusal here is a wait, never the end of a tick.</b> The bucket refills continuously, so "no slot right now"
 * means "a slot exists in {@code retryAfter}", which at the vendor's ten-per-second ceiling is of the order of a tenth
 * of a second. Ending the tick on it instead would hand the throughput ceiling straight back to the scheduler - one
 * bucket's burst per {@code drain-interval} - which is the ceiling this pacing exists to remove. The refusal that
 * genuinely ends a tick is {@link MailAccountRefusedException}: the provider saying stop, rather than the pace saying
 * not yet.
 *
 * <p>
 * The waiting is bounded twice over, because a claimed row is held for the whole of it. {@link #awaitSendSlot} never
 * waits past the drain tick's own deadline - so {@code OutboxDrain}'s budget stays the single authority on when a tick
 * ends - and it stops asking again once the context is shutting down. A shutdown is therefore delayed by at most the
 * one wait already under way, of the order of a tenth of a second at this ceiling, since the flag is read between waits
 * rather than inside one.
 *
 * <p>
 * Refusals are consumed through {@link RateLimiter#tryConsumePaced}, never {@code tryConsume}. A drain at the ceiling
 * is refused several times per message it sends, so the ordinary refusal WARN would arrive tens of times a second for
 * the whole of a large fan-out and stop meaning anything for the callers it was written for - see that method's own
 * javadoc. The refusals are still counted, under {@code outcome=paced}.
 *
 * <p>
 * The limiter is built here, from {@link RateLimiters} and {@link CommunicationProperties}, rather than registered as a
 * {@code RateLimiter}-typed bean in {@code dev.axonia.config}: a second bare {@link RateLimiter} bean would only be
 * tellable apart from any future one by Spring's parameter-name fallback, exactly the trap
 * {@code AgentSecurityConfig}'s {@code McpRateLimiters} record and {@link UnsubscribeRateLimiter} both exist to avoid.
 * Building it inside this class, the way {@link UnsubscribeRateLimiter} builds its own, keeps {@code sendRate} bound in
 * exactly one place.
 */
@Component
class SendPacing {

    static final String KEY = "global";

    /**
     * Floors a wait the limiter reported as zero. No real refusal answers "retry immediately", but one that did would
     * otherwise spin this thread against the clock for the whole of the remaining budget.
     */
    private static final Duration MINIMUM_WAIT = Duration.ofMillis(10);

    private final RateLimiter limiter;
    private final Clock clock;
    private final Waiter waiter;

    @Autowired
    SendPacing(RateLimiters rateLimiters, CommunicationProperties properties, Clock clock) {

        this(rateLimiters.create("outbox-send", properties.sendRate(), Duration.ofSeconds(1)), clock,
                SendPacing::sleep);
    }

    SendPacing(RateLimiter limiter, Clock clock, Waiter waiter) {

        this.limiter = limiter;
        this.clock = clock;
        this.waiter = waiter;
    }

    /**
     * Claims one send slot, waiting for the next one where the budget is momentarily spent. True means the caller holds
     * a slot and may send exactly one message.
     *
     * <p>
     * False means the window closed rather than that the send was refused: {@code deadline} passed while waiting, or
     * the context began shutting down, or the wait itself was interrupted. The caller sends nothing and its row goes
     * back unmarked - no attempt is burned, because nothing about the message was ever in question.
     *
     * <p>
     * {@code stopping} is read at the top of each turn round the loop, so it is read before the limiter is ever
     * consulted - a tick on its way out never spends a slot of the shared, replica-wide budget on a row it is not going
     * to send - and it is read again after each wait rather than during one, so a shutdown arriving mid-wait is
     * honoured as soon as that wait ends.
     */
    boolean awaitSendSlot(Instant deadline, BooleanSupplier stopping) {

        while (!stopping.getAsBoolean()) {
            var decision = limiter.tryConsumePaced(KEY);
            if (decision.allowed()) {
                return true;
            }
            var remaining = Duration.between(clock.instant(), deadline);
            if (!remaining.isPositive() || !waiter.await(waitFor(decision.retryAfter(), remaining))) {
                return false;
            }
        }
        return false;
    }

    private static Duration waitFor(Duration retryAfter, Duration remaining) {

        var wait = retryAfter.isPositive() ? retryAfter : MINIMUM_WAIT;
        return wait.compareTo(remaining) < 0 ? wait : remaining;
    }

    /** Restores the interrupt flag rather than swallowing it: the caller's answer to an interrupt is to stop. */
    private static boolean sleep(Duration wait) {

        try {
            Thread.sleep(wait);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** How a refused caller spends the wait, injected so a test can pin the wait without ever taking it. */
    @FunctionalInterface
    interface Waiter {

        /** False when the wait was cut short by an interrupt. */
        boolean await(Duration wait);
    }
}
