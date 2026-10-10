package dev.axonia.events.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOneOffRequest(@NotBlank String title, @NotNull @Valid ScheduledAt scheduledAt,
        @Valid MeetingPlace place, Integer durationMinutes, List<@Valid SectionTemplate> sections) {

    public CreateOneOffRequest {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
