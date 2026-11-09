package dev.axonia.health.api.spi;

import java.time.Instant;

/**
 * Read-only signals from the recognition context. Aggregate count only, by design: this port never exposes who loved or
 * who was loved. Implemented by dev.axonia.recognition.adapter.out.health.RecognitionSignalsAdapter.
 */
public interface RecognitionSignals {

    long lovesGiven(long communityId, Instant from, Instant to);
}
