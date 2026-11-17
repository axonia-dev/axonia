package dev.axonia.mentoring.api;

import java.time.LocalDate;

/** The pairing's next meeting and who set it; {@code passed} once the day is before the caller's today. */
public record NextMeetingView(LocalDate on, String setById, String setByName, boolean passed) {
}
