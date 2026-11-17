package dev.axonia.community.application;

import jakarta.validation.constraints.NotNull;

public record UpdateMentoringSettingsRequest(@NotNull Boolean enabled) {
}
