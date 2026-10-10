package dev.axonia.knowledge.api;

import java.time.Instant;
import java.util.List;

import lombok.Builder;

@Builder
public record Artifact(Long id, Long communityId, Long typeId, String type, String title, String description,
        String url, List<String> tags, String contributorUserId, Provenance provenance, ArtifactStatus status,
        int loveCount, Instant createdAt, Instant updatedAt) {
}
