package dev.axonia.knowledge.application;

import jakarta.validation.constraints.NotBlank;

public record CreateArtifactTypeRequest(@NotBlank String label, String icon, String accentColor) {
}
