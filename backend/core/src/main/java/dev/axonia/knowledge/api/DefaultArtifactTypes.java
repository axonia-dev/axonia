package dev.axonia.knowledge.api;

/** Seeds a fresh organization's artifact-type vocabulary. */
public interface DefaultArtifactTypes {

    void seedFor(String organizationId);
}
