package dev.axonia.knowledge.application;

import jakarta.validation.constraints.NotBlank;

public record UpdateArtifactTypeRequest(@NotBlank String label, String icon, String accentColor) {
}
