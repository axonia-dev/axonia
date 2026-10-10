package dev.axonia.knowledge.domain;

import java.time.Instant;
import java.util.Set;

import dev.axonia.knowledge.api.ArtifactStatus;
import lombok.Builder;

/**
 * One artifact as the library keeps it, every column included, so a save or a delete round-trips exactly. {@code tags}
 * keeps its iteration order, and {@code loveCount} is read but never written back: the love listener owns it.
 */
@Builder(toBuilder = true)
public record StoredArtifact(Long id, String organizationId, Long communityId, Long typeId, String title,
        String description, String url, String contributorUserId, Long provenanceSessionId, Long provenanceSegmentId,
        String provenanceLabel, String status, String searchDocument, Integer loveCount, Set<String> tags,
        Instant createdAt, Instant updatedAt, Long version) {

    public boolean isActive() {

        return ArtifactStatus.ACTIVE.name().equals(status);
    }

    public boolean isArchived() {

        return ArtifactStatus.ARCHIVED.name().equals(status);
    }
}
