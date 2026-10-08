package dev.axonia.events;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import dev.axonia.ValidTimeZone;

public record UpdateSeriesRequest(@NotBlank String title, @NotNull @jakarta.validation.Valid Cadence cadence,
        @NotNull LocalDate startsOn, LocalTime preferredTime, @NotBlank @ValidTimeZone String timeZone,
        @jakarta.validation.Valid MeetingPlace place, Integer durationMinutes,
        List<@jakarta.validation.Valid SectionTemplate> defaultStructure) {

    public UpdateSeriesRequest {
        defaultStructure = defaultStructure == null ? List.of() : List.copyOf(defaultStructure);
    }
}
