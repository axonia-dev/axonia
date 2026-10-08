package dev.axonia.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCommunityRequest(@NotBlank String name, @NotNull Long kindId, String description) {
}
