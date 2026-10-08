package dev.axonia.knowledge;

import lombok.Builder;

@Builder
public record Provenance(Long sessionId, Long segmentId, String label) {
}
