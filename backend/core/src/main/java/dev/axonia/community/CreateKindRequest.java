package dev.axonia.community;

import jakarta.validation.constraints.NotBlank;

public record CreateKindRequest(@NotBlank String label) {
}
