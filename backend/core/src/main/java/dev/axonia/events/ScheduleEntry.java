package dev.axonia.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.Builder;

/**
 * One item on a community's schedule: a real {@code Session} (has {@code sessionId}) or a virtual occurrence projected
 * from a series' cadence ({@code virtual = true}, no {@code sessionId} yet). Ghosts carry the series' current default
 * structure so the UI can render them open-for-sign-up.
 *
 * <p>
 * {@code place} is the effective place, resolved by {@link MeetingPlace#resolve} for a real session and taken straight
 * from the series for a ghost, which has no row of its own to hold an override.
 *
 * <p>
 * {@code hasTime} says whether anybody chose an hour for this occurrence, exactly as it does on {@link Session}. A real
 * entry reports what its row stores; a ghost reports whether its series has a preferred time, which is the rule that
 * row would have been written with, so a ghost and the session it becomes read identically.
 *
 * <p>
 * {@code seriesTimeZone} is the zone that governs this occurrence's wall clock, {@code null} for an entry that belongs
 * to no series and so has none, exactly as it is on {@link Session}. It is the fact an editor opened from the calendar
 * needs and the instant cannot give it: an occurrence of a series is composed in that series' zone whatever a request
 * states, so an hour read or written on any other clock names a different instant. A ghost reports its series' zone
 * directly, so a ghost and the session it becomes are read on the same clock.
 *
 * <p>
 * {@code version} and {@code updatedAt} exist for the calendar feed, which has to emit a {@code SEQUENCE} and a
 * {@code LAST-MODIFIED} for every occurrence it publishes. A ghost reports version {@code 0} and the series'
 * {@code updatedAt}: nothing has ever been revised about an occurrence nobody has touched, and the only revision a
 * subscriber can observe is one to the series that projects it.
 */
@Builder
public record ScheduleEntry(Long sessionId, Long seriesId, String title, LocalDate occurrenceDate, Instant scheduledAt,
        boolean hasTime, String seriesTimeZone, Integer durationMinutes, SessionStatus status, MeetingPlace place,
        Long version, Instant updatedAt, boolean virtual, List<Section> sections) {

    public ScheduleEntry {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
