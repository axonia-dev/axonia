package dev.axonia.events.application;

import jakarta.validation.constraints.NotBlank;

public record CreateSectionTypeRequest(@NotBlank String label, Boolean expectsPresenter, String icon,
        String accentColor) {
}
