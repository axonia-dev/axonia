package dev.axonia.mentoring.api;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;

/**
 * A meeting the pairing planned and no note has held yet; {@code plannedById} is null once who planned it was erased,
 * and {@code passed} once its day is before the caller's today.
 */
@Builder
public record PlannedMeetingView(long id, LocalDate on, List<Long> topicIds, String plannedById, String plannedByName,
        boolean passed) {
}
