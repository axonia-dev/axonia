package dev.axonia.knowledge;

import jakarta.validation.constraints.NotBlank;

public record UpdateArtifactTypeRequest(@NotBlank String label, String icon, String accentColor) {
}
