package dev.axonia.health.api.spi;

import java.time.Instant;

import dev.axonia.health.api.ReuseCounts;

/**
 * Read-only reuse signal from the bookmarks context. Implemented by
 * dev.axonia.bookmarks.adapter.out.health.ReuseSignalsAdapter.
 */
public interface ReuseSignals {

    /** Saves and helpful answers over [from, to). Self-saves excluded from the demand count. */
    ReuseCounts reuseCounts(long communityId, Instant from, Instant to);
}
