package dev.axonia.knowledge.domain;

import java.time.Instant;

import lombok.Builder;

/** An artifact attached to a session, or to one of its talks, every column included. */
@Builder(toBuilder = true)
public record StoredAttachment(Long id, String organizationId, Long artifactId, Long sessionId, Long segmentId,
        String attachedByUserId, Instant createdAt, Instant updatedAt, Long version) {
}
