package dev.axonia.events.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import dev.axonia.events.api.MeetingPlace;
import dev.axonia.events.api.ScheduledAt;

public record UpdateSessionRequest(@NotNull @Valid ScheduledAt scheduledAt, Integer durationMinutes,
        String facilitatorUserId, @Valid MeetingPlace place) {
}
