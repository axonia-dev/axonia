package dev.axonia.knowledge;

import lombok.Builder;

@Builder
public record ArtifactSession(Long sessionId, long count) {
}
