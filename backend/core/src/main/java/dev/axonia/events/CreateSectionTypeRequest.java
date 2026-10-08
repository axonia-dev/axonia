package dev.axonia.events;

import jakarta.validation.constraints.NotBlank;

public record CreateSectionTypeRequest(@NotBlank String label, Boolean expectsPresenter, String icon,
        String accentColor) {
}
