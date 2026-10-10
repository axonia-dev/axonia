package dev.axonia.shared.api.time;

import java.time.Clock;

import com.github.benmanes.caffeine.cache.Ticker;

/**
 * The application's {@link Clock} as a Caffeine {@link Ticker}, so a cache entry expires on the time every other
 * deadline in the application is measured on, and a test that steps the clock expires cache entries with it.
 *
 * <p>
 * Caffeine only ever subtracts two readings, so counting from the epoch is harmless, and a clock stepped backward makes
 * an entry look younger, never expired. The arithmetic is exact: an instant past 2262-04-11 has no nanosecond count in
 * a {@code long}, and reading one fails rather than wrapping into the past.
 */
public record ClockTicker(Clock clock) implements Ticker {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    @Override
    public long read() {

        var now = clock.instant();
        return Math.addExact(Math.multiplyExact(now.getEpochSecond(), NANOS_PER_SECOND), now.getNano());
    }
}
