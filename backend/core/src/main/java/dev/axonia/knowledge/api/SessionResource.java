package dev.axonia.knowledge.api;

import lombok.Builder;

@Builder
public record SessionResource(Long attachmentId, Artifact artifact, Long segmentId, String attachedByUserId) {
}
