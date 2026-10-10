package dev.axonia.events.domain;

import java.time.LocalDate;

public record SessionSkipped(Long sessionId, Long communityId, LocalDate occurrenceDate) {
}
