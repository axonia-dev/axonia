package dev.axonia.health;

import java.time.Instant;
import java.util.List;

/** Read-only artifact-opening signal from the knowledge context. Implemented in dev.axonia.knowledge.internal. */
public interface OpeningSignals {

    /**
     * Openings over [from, to). A contributor's own opens of their own artifact are excluded, as they are not demand.
     */
    OpeningCounts openingCounts(long communityId, Instant from, Instant to);

    /** Openings per bucket over [from, to), zero-filled. Self-opens excluded, exactly as in the scalar read. */
    List<Long> openingsPerBucket(long communityId, Instant from, Instant to, int buckets);
}
