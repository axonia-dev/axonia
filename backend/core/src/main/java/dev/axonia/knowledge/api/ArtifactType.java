package dev.axonia.knowledge.api;

import lombok.Builder;

@Builder
public record ArtifactType(Long id, String label, String icon, String accentColor, long artifactCount) {
}
