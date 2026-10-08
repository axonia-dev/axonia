package dev.axonia.community;

import jakarta.validation.constraints.NotBlank;

public record RenameKindRequest(@NotBlank String label) {
}
