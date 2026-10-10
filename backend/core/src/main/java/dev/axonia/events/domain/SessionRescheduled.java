package dev.axonia.events.domain;

import java.time.Instant;

public record SessionRescheduled(Long sessionId, Long communityId, Instant newScheduledAt) {
}
