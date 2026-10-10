package dev.axonia.community.adapter.in.web;

import jakarta.validation.constraints.NotNull;

import dev.axonia.community.api.CommunityRole;

public record ChangeRoleRequest(@NotNull CommunityRole role) {
}
