package dev.axonia.community.application;

import jakarta.validation.constraints.NotBlank;

public record CreateKindRequest(@NotBlank String label) {
}
