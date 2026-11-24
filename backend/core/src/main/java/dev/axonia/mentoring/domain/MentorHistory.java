package dev.axonia.mentoring.domain;

/**
 * What a mentor's pairings in one community, with both sides present and none private, say about listing them: an
 * ongoing shown one, an ended shown one, an ended one that never showed.
 */
public record MentorHistory(String mentorId, boolean ongoingShown, boolean endedShown, boolean endedUnshown) {
}
