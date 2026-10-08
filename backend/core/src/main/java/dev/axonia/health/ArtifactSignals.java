package dev.axonia.health;

import java.time.Instant;
import java.util.Map;

/** Read-only artifact-capture signal from the knowledge context. Implemented in dev.axonia.knowledge.internal. */
public interface ArtifactSignals {

    /** Artifacts captured per member in [from, to). */
    Map<String, Long> artifactCounts(long communityId, Instant from, Instant to);
}
