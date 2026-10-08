package dev.axonia.bookmarks;

import java.time.Instant;
import java.time.LocalDate;

import dev.axonia.events.SessionStatus;
import lombok.Builder;

/** Compact session summary for a saved item; {@code title} is already resolved to its display form. */
@Builder
public record SavedSession(Long id, Long communityId, String title, SessionStatus status, Instant scheduledAt,
        LocalDate occurrenceDate, String facilitatorUserId, int slotCount, String recordingUrl) {
}
