package dev.axonia.events;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import lombok.Builder;

@Builder
public record Series(Long id, Long communityId, String title, Cadence cadence, LocalDate startsOn,
        LocalTime preferredTime, String timeZone, MeetingPlace place, Integer durationMinutes, SeriesStatus status,
        List<SectionTemplate> defaultStructure, Instant nextSuggestedAt, Instant createdAt, Instant updatedAt) {

    public Series {
        defaultStructure = defaultStructure == null ? List.of() : List.copyOf(defaultStructure);
    }
}
