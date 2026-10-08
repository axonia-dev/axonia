package dev.axonia.health;

import java.time.Instant;
import java.util.List;

/** Read-only reuse signal from the bookmarks context. Implemented in dev.axonia.bookmarks.internal. */
public interface ReuseSignals {

    /** Saves and helpful answers over [from, to). Self-saves excluded from the demand count. */
    ReuseCounts reuseCounts(long communityId, Instant from, Instant to);

    /** Saves per bucket over [from, to), zero-filled. Self-saves excluded, exactly as in the scalar read. */
    List<Long> savesPerBucket(long communityId, Instant from, Instant to, int buckets);
}
