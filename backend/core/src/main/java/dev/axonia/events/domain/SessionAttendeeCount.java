package dev.axonia.events.domain;

/** How many people attended one session, for a batch count keyed by session id. */
public record SessionAttendeeCount(Long sessionId, long attendeeCount) {
}
