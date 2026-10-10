package dev.axonia.shared.api.ratelimit;

import java.time.Duration;

/**
 * Where token-bucket state lives. The port exists so the state can be shared across instances: a limiter that keeps its
 * buckets in the JVM multiplies every budget by the replica count, which this replaces.
 *
 * <p>
 * {@code key} arrives already namespaced by its limiter (see {@link RateLimiter}). The store treats it as opaque.
 */
public interface RateLimitStore {

    /**
     * Spends one token from {@code key}'s bucket, which holds {@code capacity} tokens refilling over {@code window}.
     */
    RateLimitAttempt tryConsume(String key, int capacity, Duration window);
}
