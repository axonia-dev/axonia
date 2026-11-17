package dev.axonia.knowledge.api;

/** An artifact's title, with the community whose library holds it. */
public record TitledArtifact(long communityId, String title) {
}
