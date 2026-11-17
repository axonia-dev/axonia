package dev.axonia.mentoring.api;

import jakarta.validation.constraints.NotNull;

/** Both switches are stated every time, so a missing one is refused rather than read as off. */
public record AvailabilityRequest(@NotNull Boolean takingMentees, Integer menteeLimit,
        @NotNull Boolean lookingForMentor) {
}
