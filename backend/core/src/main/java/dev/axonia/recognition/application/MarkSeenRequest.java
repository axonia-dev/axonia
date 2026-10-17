package dev.axonia.recognition.application;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/** The {@code asOf} a replay was read at; without a community, every community the member is credited in. */
public record MarkSeenRequest(@NotNull Instant asOf, Long communityId) {
}
