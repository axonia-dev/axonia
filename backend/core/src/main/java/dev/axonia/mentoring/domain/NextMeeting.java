package dev.axonia.mentoring.domain;

import java.time.Instant;
import java.time.LocalDate;

/** A pairing's next meeting: its day, who set it and when. */
public record NextMeeting(LocalDate on, String setBy, Instant setAt) {
}
