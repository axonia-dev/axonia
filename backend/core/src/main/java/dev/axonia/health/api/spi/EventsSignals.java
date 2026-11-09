package dev.axonia.health.api.spi;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.axonia.health.api.BucketCredit;
import dev.axonia.health.api.BucketTalks;
import dev.axonia.health.api.SeriesPulse;

/**
 * Read-only signals from the events context for a window [from, to). Implemented by
 * dev.axonia.events.adapter.out.health.EventsSignalsAdapter. Callers guarantee to <= now, so "scheduled in window"
 * means "held in window" for non-cancelled sessions.
 */
public interface EventsSignals {

    /**
     * Talks per presenter: one per presenter credited on a slot of a held session (neither cancelled nor skipped),
     * dated by the session.
     */
    Map<String, Long> talkCounts(long communityId, Instant from, Instant to);

    /** Topics proposed per proposer, dated by creation. */
    Map<String, Long> topicProposals(long communityId, Instant from, Instant to);

    /** The earliest session scheduled after {@code now} that is neither cancelled nor skipped. */
    Optional<Instant> nextSessionAt(long communityId, Instant now);

    /**
     * The latest session ever held before {@code now} (neither cancelled nor skipped), however long ago; empty when the
     * community has never held one.
     */
    Optional<Instant> lastHeldSessionAt(long communityId, Instant now);

    /** Talks per presenter per bucket over [from, to), bucketed by the session's schedule; empty buckets omitted. */
    List<BucketTalks> talkCreditsPerBucket(long communityId, Instant from, Instant to, int buckets);

    /** One credit per distinct proposer per bucket over [from, to), bucketed by the topic's creation. */
    List<BucketCredit> topicCreditsPerBucket(long communityId, Instant from, Instant to, int buckets);

    /**
     * One credit per distinct engaged user per bucket over [from, to): topic votes and attendance, each bucketed by its
     * own act timestamp.
     */
    List<BucketCredit> engagementCreditsPerBucket(long communityId, Instant from, Instant to, int buckets);

    List<Instant> heldSessionDates(long communityId, Instant from, Instant to);

    List<SeriesPulse> activeSeries(long communityId);

    /** Members with an engagement act in the window: topic votes or attendance. */
    Set<String> engagedUserIds(long communityId, Instant from, Instant to);
}
