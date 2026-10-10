package dev.axonia.knowledge.domain;

import java.time.Instant;

import lombok.Builder;

/** One entry of an organization's artifact-type vocabulary, every column included. */
@Builder(toBuilder = true)
public record StoredArtifactType(Long id, String organizationId, String label, String icon, String accentColor,
        Instant createdAt, Instant updatedAt, Long version) {
}
