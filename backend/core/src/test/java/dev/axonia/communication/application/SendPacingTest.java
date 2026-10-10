package dev.axonia.communication.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import dev.axonia.communication.configuration.CommunicationPropertiesMother;
import dev.axonia.shared.api.ratelimit.RateLimitAttempt;
import dev.axonia.shared.api.ratelimit.RateLimitStore;
import dev.axonia.shared.api.ratelimit.RateLimiter;
import dev.axonia.shared.api.ratelimit.RateLimiters;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pace itself, and above all what a refusal by it means. A paced refusal says "there is a slot in {@code
 * retryAfter}", which is a wait; an account refusal says "stop, all of you", which ends a tick. Conflating the two is
 * what made a backlogged organization send one bucket's burst per minute, so every test here is about which of those
 * two a given answer is.
 */
@ExtendWith(OutputCaptureExtension.class)
class SendPacingTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final Duration A_TENTH_OF_A_SECOND = Duration.ofMillis(100);

    private final Ticker ticker = new Ticker();

    /**
     * The clock and the wait in one object, so that time only ever moves because somebody waited. Tests spend the
     * tick's whole budget without spending a millisecond of anybody's real time, which is the only way a wait this deep
     * in a send path can be pinned at all.
     */
    private static final class Ticker extends Clock implements SendPacing.Waiter {

        private Instant now = NOW;
        private final List<Duration> waits = new ArrayList<>();
        private boolean interrupted;

        @Override
        public boolean await(Duration wait) {

            waits.add(wait);
            now = now.plus(wait);
            return !interrupted;
        }

        @Override
        public Instant instant() {

            return now;
        }

        @Override
        public ZoneId getZone() {

            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {

            throw new UnsupportedOperationException();
        }
    }

    /** Answers the given attempts in order, then repeats the last one forever. */
    private static RateLimiter limiterAnswering(RateLimitAttempt... attempts) {

        var queued = new ArrayDeque<>(List.of(attempts));
        RateLimitStore store = (key, capacity, window) -> queued.size() > 1 ? queued.poll() : queued.peek();
        return new RateLimiters(store, new SimpleMeterRegistry()).create("outbox-send", 10, Duration.ofSeconds(1));
    }

    private SendPacing pacingOver(RateLimiter limiter) {

        return new SendPacing(limiter, ticker, ticker);
    }

    private static Instant deadlineIn(Duration budget) {

        return NOW.plus(budget);
    }

    @Test
    void takesASlotWithoutWaitingWhileThePaceHasRoom() {

        var pacing = pacingOver(limiterAnswering(RateLimitAttempt.allowed()));

        assertThat(pacing.awaitSendSlot(deadlineIn(Duration.ofSeconds(45)), () -> false)).isTrue();
        assertThat(ticker.waits).isEmpty();
    }

    /**
     * The whole point of the fix. The bucket refills, so a refusal names the moment the next slot exists; waiting that
     * out and asking again keeps the tick sending at the vendor's ceiling, where returning false would have handed the
     * ceiling back to the scheduler.
     */
    @Test
    void waitsOutAPacedRefusalAndThenTakesTheSlot() {

        var pacing = pacingOver(limiterAnswering(RateLimitAttempt.refused(A_TENTH_OF_A_SECOND),
                RateLimitAttempt.refused(A_TENTH_OF_A_SECOND), RateLimitAttempt.allowed()));

        assertThat(pacing.awaitSendSlot(deadlineIn(Duration.ofSeconds(45)), () -> false)).isTrue();
        assertThat(ticker.waits).containsExactly(A_TENTH_OF_A_SECOND, A_TENTH_OF_A_SECOND);
    }

    /**
     * A claimed row is held for the whole of a wait, so the wait may never outlive the tick that claimed it: the drain
     * budget stays the single authority on when a tick ends. The one wait taken is the remaining budget, not the ten
     * seconds the limiter asked for.
     */
    @Test
    void neverWaitsPastTheTicksOwnDeadline() {

        var pacing = pacingOver(limiterAnswering(RateLimitAttempt.refused(Duration.ofSeconds(10))));

        assertThat(pacing.awaitSendSlot(deadlineIn(Duration.ofSeconds(2)), () -> false)).isFalse();
        assertThat(ticker.waits).containsExactly(Duration.ofSeconds(2));
    }

    /** A budget already spent is answered at once: no wait, and nothing for the caller to hold a row through. */
    @Test
    void givesUpWithoutWaitingOnceTheDeadlineHasPassed() {

        var pacing = pacingOver(limiterAnswering(RateLimitAttempt.refused(A_TENTH_OF_A_SECOND)));

        assertThat(pacing.awaitSendSlot(NOW, () -> false)).isFalse();
        assertThat(ticker.waits).isEmpty();
    }

    /**
     * A shutdown short-circuits ahead of the limiter, never after it: a tick on its way out must not spend a slot of
     * the shared, replica-wide budget on a row it is not going to send.
     */
    @Test
    void aStoppingTickNeitherWaitsNorSpendsASlot() {

        var asked = new AtomicInteger();
        RateLimitStore store = (key, capacity, window) -> {
            asked.incrementAndGet();
            return RateLimitAttempt.allowed();
        };
        var limiter = new RateLimiters(store, new SimpleMeterRegistry()).create("outbox-send", 10,
                Duration.ofSeconds(1));

        assertThat(pacingOver(limiter).awaitSendSlot(deadlineIn(Duration.ofSeconds(45)), () -> true)).isFalse();
        assertThat(asked).hasValue(0);
        assertThat(ticker.waits).isEmpty();
    }

    /** An interrupt is the other way a wait ends early, and it ends the whole attempt rather than being ignored. */
    @Test
    void anInterruptedWaitGivesUpRatherThanAskingAgain() {

        ticker.interrupted = true;
        var pacing = pacingOver(limiterAnswering(RateLimitAttempt.refused(A_TENTH_OF_A_SECOND)));

        assertThat(pacing.awaitSendSlot(deadlineIn(Duration.ofSeconds(45)), () -> false)).isFalse();
        assertThat(ticker.waits).containsExactly(A_TENTH_OF_A_SECOND);
    }

    /**
     * No real refusal answers "retry immediately", but one that did would otherwise spin this thread against the clock
     * for the whole remaining budget - a worker burning a core and holding a claimed row for 45 seconds.
     */
    @Test
    void floorsAWaitTheLimiterReportedAsZero() {

        var pacing = pacingOver(limiterAnswering(RateLimitAttempt.refused(Duration.ZERO), RateLimitAttempt.allowed()));

        assertThat(pacing.awaitSendSlot(deadlineIn(Duration.ofSeconds(45)), () -> false)).isTrue();
        assertThat(ticker.waits).containsExactly(Duration.ofMillis(10));
    }

    /**
     * The shipped wait, which every test above replaces with a seam. It really does sleep, and it really does ask again
     * afterwards rather than treating its own refusal as an answer - one millisecond here, of the order of a hundred in
     * production.
     */
    @Test
    void theShippedWaitSleepsAndThenAsksAgain() {

        var pacing = shippedPacing(RateLimitAttempt.refused(Duration.ofMillis(1)), RateLimitAttempt.allowed());

        assertThat(pacing.awaitSendSlot(deadlineIn(Duration.ofSeconds(45)), () -> false)).isTrue();
    }

    /**
     * An interrupt is how a shutdown reaches a thread already inside the sleep. It gives up rather than swallowing the
     * interrupt and asking again, and it leaves the flag set so whatever runs next on this thread still sees it.
     *
     * <p>
     * The second answer is {@code allowed} on purpose: an implementation that swallowed the interrupt would sleep its
     * millisecond, ask again, be allowed and fail this assertion at once, rather than waiting out a long
     * {@code retryAfter} and turning a regression into a hang.
     */
    @Test
    void anInterruptedShippedWaitGivesUpAndLeavesTheThreadInterrupted() {

        var pacing = shippedPacing(RateLimitAttempt.refused(Duration.ofMillis(1)), RateLimitAttempt.allowed());
        Thread.currentThread().interrupt();
        try {
            assertThat(pacing.awaitSendSlot(deadlineIn(Duration.ofSeconds(45)), () -> false)).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    /** Built through the constructor Spring uses, so the wait is the real {@code Thread.sleep} rather than a seam. */
    private static SendPacing shippedPacing(RateLimitAttempt... attempts) {

        var queued = new ArrayDeque<>(List.of(attempts));
        RateLimitStore store = (key, capacity, window) -> queued.size() > 1 ? queued.poll() : queued.peek();
        return new SendPacing(new RateLimiters(store, new SimpleMeterRegistry()), CommunicationPropertiesMother.any(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * A drain at the ceiling is refused several times per message it sends, so routing those refusals through the
     * limiter's ordinary path would emit tens of WARN lines a second for the whole of a large fan-out - and would leave
     * that line, whose entire value is being rare, meaning "the drain is working". The refusals are counted under
     * {@code outcome=paced} instead, and said nowhere.
     */
    @Test
    void aPacedRefusalIsCountedButNeverLogged(CapturedOutput output) {

        var meters = new SimpleMeterRegistry();
        RateLimitStore store = (key, capacity, window) -> RateLimitAttempt.refused(A_TENTH_OF_A_SECOND);
        var pacing = new SendPacing(new RateLimiters(store, meters).create("outbox-send", 10, Duration.ofSeconds(1)),
                ticker, ticker);

        assertThat(pacing.awaitSendSlot(deadlineIn(Duration.ofMillis(250)), () -> false)).isFalse();

        assertThat(ticker.waits).hasSize(3);
        assertThat(output.getOut()).doesNotContain("Rate limit exhausted");
        assertThat(
                meters.get("axonia.rate_limit").tag("limiter", "outbox-send").tag("outcome", "paced").counter().count())
                .isEqualTo(4);
    }

    /**
     * The degraded mode is inherited rather than defended against: shared state unreachable means the send is allowed,
     * counted and logged, because a paused mail queue is worse than a brief overshoot an operator can see.
     */
    @Test
    void aLimiterThatCannotReachItsSharedStateStillAllowsTheSend() {

        var pacing = pacingOver(limiterAnswering(RateLimitAttempt.degraded()));

        assertThat(pacing.awaitSendSlot(deadlineIn(Duration.ofSeconds(45)), () -> false)).isTrue();
        assertThat(ticker.waits).isEmpty();
    }
}
