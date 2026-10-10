package dev.axonia.events.api;

import jakarta.validation.constraints.NotNull;

public record SectionTemplate(@NotNull Long sectionTypeId, String title, Integer durationMinutes) {
}
