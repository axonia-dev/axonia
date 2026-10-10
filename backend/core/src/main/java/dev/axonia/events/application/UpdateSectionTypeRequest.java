package dev.axonia.events.application;

import jakarta.validation.constraints.NotBlank;

public record UpdateSectionTypeRequest(@NotBlank String label, Boolean expectsPresenter, String icon,
        String accentColor) {
}
