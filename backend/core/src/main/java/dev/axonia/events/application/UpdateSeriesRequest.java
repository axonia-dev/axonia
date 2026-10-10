package dev.axonia.events.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import dev.axonia.events.api.MeetingPlace;
import dev.axonia.events.api.SectionTemplate;
import dev.axonia.events.domain.Cadence;
import dev.axonia.shared.api.validation.ValidTimeZone;

public record UpdateSeriesRequest(@NotBlank String title, @NotNull @jakarta.validation.Valid Cadence cadence,
        @NotNull LocalDate startsOn, LocalTime preferredTime, @NotBlank @ValidTimeZone String timeZone,
        @jakarta.validation.Valid MeetingPlace place, Integer durationMinutes,
        List<@jakarta.validation.Valid SectionTemplate> defaultStructure) {

    public UpdateSeriesRequest {
        defaultStructure = defaultStructure == null ? List.of() : List.copyOf(defaultStructure);
    }
}
