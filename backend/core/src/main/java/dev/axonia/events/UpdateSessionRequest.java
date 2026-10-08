package dev.axonia.events;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateSessionRequest(@NotNull @Valid ScheduledAt scheduledAt, Integer durationMinutes,
        String facilitatorUserId, @Valid MeetingPlace place) {
}
