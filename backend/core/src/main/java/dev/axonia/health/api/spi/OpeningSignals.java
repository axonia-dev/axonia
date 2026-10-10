package dev.axonia.health.api.spi;

import java.time.Instant;
import java.util.List;

import dev.axonia.health.api.OpeningCounts;

/**
 * Read-only artifact-opening signal from the knowledge context. Implemented by
 * dev.axonia.knowledge.adapter.out.health.OpeningSignalsAdapter.
 */
public interface OpeningSignals {

    /**
     * Openings over [from, to). A contributor's own opens of their own artifact are excluded, as they are not demand.
     */
    OpeningCounts openingCounts(long communityId, Instant from, Instant to);

    /** Openings per bucket over [from, to), zero-filled. Self-opens excluded, exactly as in the scalar read. */
    List<Long> openingsPerBucket(long communityId, Instant from, Instant to, int buckets);
}
