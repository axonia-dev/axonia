package dev.axonia.community.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCommunicationSettingsRequest(@NotNull Boolean notifySessionUpcoming,
        @NotNull Boolean notifySessionWrapUp, @NotNull @Min(0) @Max(30) Integer sessionUpcomingLeadDays,
        @NotNull @Min(0) @Max(336) Integer wrapUpGraceHours) {
}
