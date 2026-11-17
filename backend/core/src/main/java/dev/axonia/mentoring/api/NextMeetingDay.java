package dev.axonia.mentoring.api;

import java.time.LocalDate;

/** A pairing's next meeting day; {@code passed} once the day is before the caller's today. */
public record NextMeetingDay(LocalDate on, boolean passed) {
}
