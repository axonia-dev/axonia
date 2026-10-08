package dev.axonia;

import java.time.Duration;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Builds the application's rate limiters, holding the two things every one of them needs.
 *
 * <p>
 * This replaces the {@code RateLimiterConfig} parameter record. That record existed only to keep a five-argument
 * constructor away from PMD's {@code ExcessiveParameterList}, and adding the store to it would have pushed the pressure
 * outward instead: {@code ExportDownload} would have reached five constructor parameters of its own. A factory removes
 * the pressure at every call site at once, and it means no call site has to know that a limiter needs a store or a
 * meter registry at all.
 */
public final class RateLimiters {

    private final RateLimitStore store;
    private final MeterRegistry meters;

    public RateLimiters(RateLimitStore store, MeterRegistry meters) {

        this.store = store;
        this.meters = meters;
    }

    /** A limiter named {@code name} allowing {@code limit} attempts per {@code window} per caller. */
    public RateLimiter create(String name, int limit, Duration window) {

        return new RateLimiter(name, new Budget(limit, window), store, meters);
    }
}
