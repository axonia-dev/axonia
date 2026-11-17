package dev.axonia.mentoring.api;

import java.time.LocalDate;

/** The day of the pairing's next meeting, today or later in the caller's zone. */
public record NextMeetingRequest(LocalDate on) {
}
