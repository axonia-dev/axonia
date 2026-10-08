package dev.axonia.health;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only signals from the events context for a window [from, to). Implemented in dev.axonia.events.internal. Callers
 * guarantee to <= now, so "scheduled in window" means "held in window" for non-cancelled sessions.
 */
public interface EventsSignals {

    /**
     * Contribution acts per member: presenter spots on held sessions and topics proposed. Session materialization and
     * series creation persist no actor and are deliberately not counted (honest degradation).
     */
    Map<String, Long> contributionCounts(long communityId, Instant from, Instant to);

    List<Instant> heldSessionDates(long communityId, Instant from, Instant to);

    List<SeriesPulse> activeSeries(long communityId);

    /**
     * Distinct contributors per bucket over [from, to), zero-filled.
     *
     * <p>
     * This is not a bus-factor series and must never be labelled as one. A bus factor over a single bucket is a
     * different statistic from the window's, and on a sparse bucket it reads 1 or 0 for a thriving community, which
     * would render sparsity as fragility. This counts how many different people contributed in each bucket.
     */
    List<Long> distinctContributorsPerBucket(long communityId, Instant from, Instant to, int buckets);

    /** Members with an engagement act in the window: topic votes or attendance. */
    Set<String> engagedUserIds(long communityId, Instant from, Instant to);

    /** Distinct members with an engagement act (topic vote or attendance) per bucket over [from, to), zero-filled. */
    List<Long> engagedMembersPerBucket(long communityId, Instant from, Instant to, int buckets);
}
