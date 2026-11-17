package dev.axonia.mentoring.api;

import java.time.LocalDate;
import java.util.List;

/** {@code meetingOn} is today when null; {@code topicIds} name topics of the same pairing, none when null. */
public record NoteRequest(String body, LocalDate meetingOn, List<Long> topicIds) {
}
