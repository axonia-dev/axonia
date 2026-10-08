package dev.axonia.events;

import jakarta.validation.constraints.NotNull;

public record SectionTemplate(@NotNull Long sectionTypeId, String title, Integer durationMinutes) {
}
