package dev.axonia.knowledge.application.port.out;

/** The denormalized appreciation count on each artifact. */
public interface ArtifactLoveCounts {

    void increment(Long artifactId, Long communityId);

    /** Never below zero. */
    void decrement(Long artifactId, Long communityId);
}
