package dev.axonia.knowledge.domain;

import lombok.Builder;

@Builder
public record ArtifactSession(Long sessionId, long count) {
}
