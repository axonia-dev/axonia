package dev.axonia.community;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull CommunityRole role) {
}
