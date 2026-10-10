package dev.axonia.community.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateCommunityRequest(@NotBlank String name, @NotNull Long kindId, String description) {
}
