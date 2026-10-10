package dev.axonia.community.application;

import jakarta.validation.constraints.NotBlank;

public record RenameKindRequest(@NotBlank String label) {
}
