package dev.axonia.events;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record EnsureOccurrenceRequest(@NotNull LocalDate occurrenceDate) {
}
