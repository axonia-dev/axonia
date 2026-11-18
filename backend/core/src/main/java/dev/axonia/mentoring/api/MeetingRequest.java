package dev.axonia.mentoring.api;

import java.time.LocalDate;
import java.util.List;

/** A meeting's day, today or later in the caller's zone, and the board topics it will take up. */
public record MeetingRequest(LocalDate on, List<Long> topicIds) {
}
