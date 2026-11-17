package dev.axonia.mentoring.domain;

import java.time.Instant;
import java.time.LocalDate;

import lombok.Builder;

/** {@code meetingOn} is the day of the meeting the note is about, in its author's zone. */
@Builder(toBuilder = true)
public record Note(Long id, long pairingId, String authorUserId, String body, LocalDate meetingOn, Instant writtenAt,
        Instant editedAt) {
}
