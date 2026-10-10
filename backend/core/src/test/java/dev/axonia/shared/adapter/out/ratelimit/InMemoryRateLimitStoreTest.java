package dev.axonia.shared.adapter.out.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.axonia.SteppableClock;
import dev.axonia.shared.api.ratelimit.RateLimitAttempt;
import dev.axonia.shared.api.ratelimit.RateLimitAttempt.Outcome;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimitStoreTest {

    private final SteppableClock clock = new SteppableClock(Instant.parse("2026-09-26T08:00:00Z"));
    private final InMemoryRateLimitStore sut = new InMemoryRateLimitStore(clock);

    @Test
    void spendsTheBucketThenRefuses() {

        assertThat(sut.tryConsume("k", 2, Duration.ofMinutes(1)).outcome()).isEqualTo(Outcome.ALLOWED);
        assertThat(sut.tryConsume("k", 2, Duration.ofMinutes(1)).outcome()).isEqualTo(Outcome.ALLOWED);
        assertThat(sut.tryConsume("k", 2, Duration.ofMinutes(1)).outcome()).isEqualTo(Outcome.REFUSED);
    }

    @Test
    void refusalSaysHowLongUntilATokenExists() {

        sut.tryConsume("k", 1, Duration.ofMinutes(1));

        assertThat(sut.tryConsume("k", 1, Duration.ofMinutes(1)).retryAfter()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void refillsLazilyWithTime() {

        sut.tryConsume("k", 1, Duration.ofMinutes(1));
        clock.advance(Duration.ofMinutes(1));

        assertThat(sut.tryConsume("k", 1, Duration.ofMinutes(1)).outcome()).isEqualTo(Outcome.ALLOWED);
    }

    /**
     * The one-token boundary at a one-hour window: spending the only token refuses immediately, but the bucket is still
     * not full exactly one window later - one millisecond past it, instead. That extra millisecond is the
     * 14-significant-digit rounding described in the class Javadoc, not an arithmetic mistake: one step earlier, the
     * stored tokens value was truncated down from {@code 0.9999997222222222} to {@code 0.99999972222222}, so the refill
     * one millisecond later lands at {@code 0.9999999999999978} - a hair under 1, not at it.
     * {@code RedisRateLimitStoreTest} proves a real Redis round trip agrees.
     */
    @Test
    void theStoredRoundingDelaysFullByOneMillisecond() {

        assertThat(sut.tryConsume("k", 1, Duration.ofHours(1)).outcome()).isEqualTo(Outcome.ALLOWED);
        assertThat(sut.tryConsume("k", 1, Duration.ofHours(1)).outcome()).isEqualTo(Outcome.REFUSED);

        clock.advance(Duration.ofHours(1).minusMillis(1));
        assertThat(sut.tryConsume("k", 1, Duration.ofHours(1)).outcome()).as("one millisecond short of the window")
                .isEqualTo(Outcome.REFUSED);

        clock.advance(Duration.ofMillis(1));
        assertThat(sut.tryConsume("k", 1, Duration.ofHours(1)).outcome()).as(
                "exactly one window later - still refused, because the stored value was rounded down one step earlier")
                .isEqualTo(Outcome.REFUSED);

        clock.advance(Duration.ofMillis(1));
        assertThat(sut.tryConsume("k", 1, Duration.ofHours(1)).outcome()).as("one millisecond past the window")
                .isEqualTo(Outcome.ALLOWED);
    }

    /**
     * A clock that steps backward - a leap second, an NTP correction - must never refill negatively: {@code
     * Math.max(0, now - at)} in the store mirrors the Lua script's {@code math.max(0, now - at)} for exactly this case.
     */
    @Test
    void aBackwardClockStepNeverRefillsNegatively() {

        sut.tryConsume("k", 1, Duration.ofMinutes(1));
        clock.advance(Duration.ofSeconds(-30));

        var attempt = sut.tryConsume("k", 1, Duration.ofMinutes(1));

        // Refill floored at zero elapsed time: the bucket is exactly as spent as it was before the backward step, so
        // the wait is a full window, not a negative or shortened one.
        assertThat(attempt.outcome()).isEqualTo(Outcome.REFUSED);
        assertThat(attempt.retryAfter()).isEqualTo(Duration.ofMinutes(1));
    }

    /**
     * Cross-checks {@code retryAfter} against the script's own formula, {@code ceil((1 - tokens) * windowMillis /
     * capacity)}, computed here independently from inputs chosen to land on an exact binary fraction (16384 / 65536 =
     * 0.25) so the comparison is not itself at the mercy of floating-point rounding.
     */
    @Test
    void retryAfterMatchesTheScriptsFormulaExactly() {

        long windowMillis = 65536;
        int capacity = 1;
        sut.tryConsume("k", capacity, Duration.ofMillis(windowMillis));
        clock.advance(Duration.ofMillis(16384));

        double tokensBeforeThisAttempt = (double) 16384 * capacity / windowMillis;
        long expectedRetryAfterMillis = (long) Math.ceil((1 - tokensBeforeThisAttempt) * windowMillis / capacity);

        assertThat(sut.tryConsume("k", capacity, Duration.ofMillis(windowMillis)).retryAfter())
                .isEqualTo(Duration.ofMillis(expectedRetryAfterMillis));
    }

    @Test
    void keysDoNotShareABudget() {

        sut.tryConsume("k", 1, Duration.ofMinutes(1));

        assertThat(sut.tryConsume("other", 1, Duration.ofMinutes(1)).outcome()).isEqualTo(Outcome.ALLOWED);
    }

    /**
     * Redis keeps a key through the millisecond its {@code PEXPIRE} names and drops it the millisecond after
     * ({@code now > when}), so at {@code fullAt} itself the script still refills from the stored, rounded tokens: here
     * {@code 0.72263333333333 + 16642 / 60000}, a hair under 1, refused for one more millisecond. The millisecond
     * after, the bucket is gone and the attempt finds a full one.
     */
    @Test
    void keepsABucketThroughItsFullAtMillisecondAsRedisKeepsAKey() {

        // given
        var minute = Duration.ofMinutes(1);
        sut.tryConsume("k", 1, minute);
        clock.advance(Duration.ofMillis(43_358));
        var refused = sut.tryConsume("k", 1, minute);

        // when
        clock.advance(Duration.ofMillis(16_642));
        var atFullAt = sut.tryConsume("k", 1, minute);
        clock.advance(Duration.ofMillis(1));
        var theMillisecondAfter = sut.tryConsume("k", 1, minute);

        // then
        assertThat(refused).isEqualTo(RateLimitAttempt.refused(Duration.ofMillis(16_642)));
        assertThat(atFullAt).isEqualTo(RateLimitAttempt.refused(Duration.ofMillis(1)));
        assertThat(theMillisecondAfter).isEqualTo(RateLimitAttempt.allowed());
    }

    /**
     * A read sees a bucket's expiry to the millisecond; the memory is reclaimed by Caffeine's timer wheel, whose finest
     * step is about a second, so this looks five seconds on rather than at the sweep a minute later.
     */
    @Test
    void dropsABucketSoonAfterItsFullAtRatherThanAtTheNextSweep() {

        // given
        sut.tryConsume("k", 1, Duration.ofSeconds(1));

        // when
        clock.advance(Duration.ofSeconds(5));

        // then
        assertThat(sut.size()).isZero();
    }

    /** Two minutes is past both the bucket's {@code fullAt} and the timer wheel's step, so only "other" is held. */
    @Test
    void forgetsABucketOnceItWouldBeFull() {

        sut.tryConsume("k", 1, Duration.ofMinutes(1));
        clock.advance(Duration.ofMinutes(2));
        sut.tryConsume("other", 1, Duration.ofMinutes(1));

        assertThat(sut.size()).isEqualTo(1);
    }

    /** Thirty seconds into a one-minute window a spent bucket is half refilled, and still held. */
    @Test
    void keepsASpentBucketUntilItWouldBeFull() {

        sut.tryConsume("k", 1, Duration.ofMinutes(1));
        clock.advance(Duration.ofSeconds(30));
        sut.tryConsume("other", 1, Duration.ofMinutes(1));

        assertThat(sut.size()).isEqualTo(2);
    }

    /**
     * Each bucket expires at its own {@code fullAt}, not after some fixed time: capacity 1 over 10 minutes is nowhere
     * near full 61 seconds in. An {@code Expiry} that ignored {@code fullAt} - a fixed minute, say - would drop "k"
     * here and turn the final REFUSED into ALLOWED.
     */
    @Test
    void keepsABucketStillWithinItsWindow() {

        sut.tryConsume("k", 1, Duration.ofMinutes(10));
        clock.advance(Duration.ofSeconds(61));
        sut.tryConsume("other", 1, Duration.ofMinutes(10));

        assertThat(sut.size()).isEqualTo(2);
        assertThat(sut.tryConsume("k", 1, Duration.ofMinutes(10)).outcome()).isEqualTo(Outcome.REFUSED);
    }

    /** The keys are client addresses an attacker chooses, so the store needs a bound of its own, as Redis has. */
    @Test
    void holdsNoMoreBucketsThanItsBound() {

        // when
        for (var i = 0; i <= InMemoryRateLimitStore.MAX_BUCKETS; i++) {
            sut.tryConsume("k" + i, 1, Duration.ofHours(1));
        }

        // then
        assertThat(sut.size()).isLessThanOrEqualTo(InMemoryRateLimitStore.MAX_BUCKETS);
    }

    /**
     * Caffeine's {@code asMap().compute} is a {@code ConcurrentHashMap.compute} underneath and holds one key's bin lock
     * for the whole remapping function, so many threads spending the same key concurrently must still see exactly
     * {@code capacity} allowed outcomes - never more, from a lost update, and never fewer. A fixed clock removes any
     * chance of the assertion being saved, or broken, by a real refill racing the test itself.
     */
    @Test
    void underConcurrencyExactlyCapacityAttemptsAreAllowedForOneKey() throws InterruptedException {

        var concurrentSut = new InMemoryRateLimitStore(
                Clock.fixed(Instant.parse("2026-09-26T08:00:00Z"), ZoneOffset.UTC));
        int capacity = 50;
        int threads = 20;
        int attemptsPerThread = 25;
        var allowed = new AtomicInteger();
        var ready = new CountDownLatch(threads);
        var go = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        await(go);
                        for (int i = 0; i < attemptsPerThread; i++) {
                            if (concurrentSut.tryConsume("shared", capacity, Duration.ofMinutes(1))
                                    .outcome() == Outcome.ALLOWED) {
                                allowed.incrementAndGet();
                            }
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            await(ready);
            go.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).as("every thread finished within the timeout").isTrue();
        } finally {
            pool.shutdown();
        }

        assertThat(allowed.get()).isEqualTo(capacity);
    }

    private static void await(CountDownLatch latch) {

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
