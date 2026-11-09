package dev.axonia.health.api.spi;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import dev.axonia.health.api.BucketCredit;

/**
 * Read-only artifact-capture signal from the knowledge context. Implemented by
 * dev.axonia.knowledge.adapter.out.health.ArtifactSignalsAdapter.
 */
public interface ArtifactSignals {

    /** Artifacts captured per member in [from, to). */
    Map<String, Long> artifactCounts(long communityId, Instant from, Instant to);

    /**
     * Active artifacts captured per bucket over [from, to), zero-filled, one grouped query. Archived artifacts are not
     * counted, since members no longer see them in the Library; {@link #artifactCounts} keeps counting them. Buckets
     * are equal spans ending at {@code to}, so with a day per bucket each is a rolling 24 hours, not a calendar day.
     */
    List<Long> artifactsPerBucket(long communityId, Instant from, Instant to, int buckets);

    /**
     * One credit per distinct contributor per bucket over [from, to), bucketed by capture time. Archived artifacts are
     * included, matching {@link #artifactCounts}.
     */
    List<BucketCredit> artifactCreditsPerBucket(long communityId, Instant from, Instant to, int buckets);
}
