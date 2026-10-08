package dev.axonia.knowledge;

import java.time.Instant;

import lombok.Builder;

@Builder
public record Attachment(Long id, Long artifactId, Long sessionId, Long segmentId, String attachedByUserId,
        Instant createdAt) {
}
