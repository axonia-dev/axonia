package dev.axonia.events;

import java.time.LocalDate;

public record SessionSkipped(Long sessionId, Long communityId, LocalDate occurrenceDate) {
}
