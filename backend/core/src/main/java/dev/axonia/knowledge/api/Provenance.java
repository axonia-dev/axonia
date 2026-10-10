package dev.axonia.knowledge.api;

import lombok.Builder;

@Builder
public record Provenance(Long sessionId, Long segmentId, String label) {
}
