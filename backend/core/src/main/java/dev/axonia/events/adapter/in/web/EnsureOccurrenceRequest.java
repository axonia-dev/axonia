package dev.axonia.events.adapter.in.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record EnsureOccurrenceRequest(@NotNull LocalDate occurrenceDate) {
}
