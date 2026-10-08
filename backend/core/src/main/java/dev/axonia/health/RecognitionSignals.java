package dev.axonia.health;

import java.time.Instant;
import java.util.List;

/**
 * Read-only signals from the recognition context. Aggregate count only, by design: this port never exposes who loved or
 * who was loved. Implemented in dev.axonia.recognition.internal.
 */
public interface RecognitionSignals {

    long lovesGiven(long communityId, Instant from, Instant to);

    /**
     * Loves given per bucket over [from, to), split into {@code buckets} equal parts, zero-filled.
     *
     * <p>
     * Takes a bucket count rather than a list of sub-windows on purpose: an implementation handed a count cannot loop
     * over sub-windows, so one grouped query is the only way to satisfy the signature.
     */
    List<Long> lovesPerBucket(long communityId, Instant from, Instant to, int buckets);
}
