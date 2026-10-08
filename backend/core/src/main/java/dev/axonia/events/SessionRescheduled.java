package dev.axonia.events;

import java.time.Instant;

public record SessionRescheduled(Long sessionId, Long communityId, Instant newScheduledAt) {
}
