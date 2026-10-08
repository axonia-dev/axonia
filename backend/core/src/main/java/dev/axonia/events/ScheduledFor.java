package dev.axonia.events;

import java.time.Instant;

import lombok.Builder;

// Where a SCHEDULED topic is queued: the session that carries it (date) and a human label for
// it - the carrying series' title for a series occurrence, or the session's own title for a one-off.
@Builder
public record ScheduledFor(Long sessionId, Instant scheduledAt, String scheduleLabel) {
}
