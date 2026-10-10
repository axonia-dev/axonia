package dev.axonia.health.api.spi;

import java.time.Instant;
import java.util.Map;

/**
 * Read-only artifact-capture signal from the knowledge context. Implemented by
 * dev.axonia.knowledge.adapter.out.health.ArtifactSignalsAdapter.
 */
public interface ArtifactSignals {

    /** Artifacts captured per member in [from, to). */
    Map<String, Long> artifactCounts(long communityId, Instant from, Instant to);
}
