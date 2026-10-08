package dev.axonia.events;

import java.time.Instant;

import lombok.Builder;

@Builder
public record Attendance(Long id, Long sessionId, String userId, Instant createdAt) {
}
