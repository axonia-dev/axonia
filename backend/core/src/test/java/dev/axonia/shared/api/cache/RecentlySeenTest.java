package dev.axonia.shared.api.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import dev.axonia.SteppableClock;

import static org.assertj.core.api.Assertions.assertThat;

class RecentlySeenTest {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final SteppableClock clock = new SteppableClock(Instant.parse("2026-09-23T06:00:00Z"));

    @Test
    void aKeyIsClaimedOncePerWindow() {

        // given
        var sut = new RecentlySeen(WINDOW, 10, clock);

        // when
        var first = sut.claim("key");
        clock.advance(WINDOW.minusMillis(1));
        var second = sut.claim("key");

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(sut.isDue("key")).isFalse();
    }

    @Test
    void aKeyIsClaimedAgainOneWindowAfterItWasClaimed() {

        // given
        var sut = new RecentlySeen(WINDOW, 10, clock);
        sut.claim("key");
        clock.advance(WINDOW);

        // when
        var actual = sut.claim("key");

        // then
        assertThat(actual).isTrue();
    }

    /**
     * A claim that loses leaves the window where the winning one started it. Caffeine restarts an entry's write time on
     * any {@code compute} that returns a value, the old one included, so a claim written as one would push a busy key's
     * window forward on every losing call and the key would never fall due again.
     */
    @Test
    void aLosingClaimDoesNotRestartTheWindow() {

        // given
        var sut = new RecentlySeen(WINDOW, 10, clock);
        sut.claim("key");
        clock.advance(WINDOW.dividedBy(2));
        sut.claim("key");

        // when
        clock.advance(WINDOW.dividedBy(2));

        // then
        assertThat(sut.claim("key")).isTrue();
    }

    @Test
    void exactlyOneOfManyRacingClaimsWins() {

        // given
        var sut = new RecentlySeen(WINDOW, 10, clock);
        var wins = new AtomicInteger();
        var start = new CountDownLatch(1);

        // when
        try (var pool = Executors.newFixedThreadPool(16)) {
            for (var i = 0; i < 16; i++) {
                pool.submit(() -> {
                    start.await();
                    if (sut.claim("key")) {
                        wins.incrementAndGet();
                    }
                    return null;
                });
            }
            start.countDown();
        }

        // then
        assertThat(wins).hasValue(1);
    }

    @Test
    void aKeyIsDueUntilItIsRemembered() {

        // given
        var sut = new RecentlySeen(WINDOW, 10, clock);
        var before = sut.isDue("key");

        // when
        sut.remember("key");

        // then
        assertThat(before).isTrue();
        assertThat(sut.isDue("key")).isFalse();
    }

    @Test
    void aRememberedKeyIsDueAgainOneWindowLater() {

        // given
        var sut = new RecentlySeen(WINDOW, 10, clock);
        sut.remember("key");
        clock.advance(WINDOW.minusMillis(1));
        var justBefore = sut.isDue("key");

        // when
        clock.advance(Duration.ofMillis(1));

        // then
        assertThat(justBefore).isFalse();
        assertThat(sut.isDue("key")).isTrue();
    }

    /** A clock stepped backward, by NTP or by a test resetting it, makes a key look younger, never due early. */
    @Test
    void aKeyStaysNotDueWhenTheClockStepsBackward() {

        // given
        var sut = new RecentlySeen(WINDOW, 10, clock);
        sut.remember("key");

        // when
        clock.advance(Duration.ofMinutes(-30));

        // then
        assertThat(sut.isDue("key")).isFalse();
    }

    /**
     * The old memory cleared every key when it reached its cap, so every key fell due at once. Which single key goes
     * now is Caffeine's size policy, and it may be the newest one, so this counts rather than names.
     */
    @Test
    void reachingTheCapForgetsOneKeyRatherThanEveryKey() {

        // given
        var sut = new RecentlySeen(WINDOW, 10, clock);
        IntStream.range(0, 10).forEach(i -> sut.claim("key-" + i));

        // when
        sut.claim("key-10");

        // then
        assertThat(IntStream.range(0, 10).filter(i -> sut.isDue("key-" + i)).count())
                .as("keys due again after the cap was reached").isLessThanOrEqualTo(1);
    }

    @Test
    void neverHoldsMoreKeysThanItsCap() {

        // given
        var sut = new RecentlySeen(WINDOW, 10, clock);

        // when
        IntStream.range(0, 1_000).forEach(i -> sut.claim("key-" + i));

        // then
        assertThat(IntStream.range(0, 1_000).filter(i -> !sut.isDue("key-" + i)).count()).isLessThanOrEqualTo(10);
    }

    /** A full memory never stops a newcomer: the work is done, at worst once more than strictly needed. */
    @Test
    void aKeyItHasNotSeenIsDueEvenWhenItIsFull() {

        // given
        var sut = new RecentlySeen(WINDOW, 1, clock);
        sut.remember("first");

        // when
        var actual = sut.isDue("second");

        // then
        assertThat(actual).isTrue();
    }
}
