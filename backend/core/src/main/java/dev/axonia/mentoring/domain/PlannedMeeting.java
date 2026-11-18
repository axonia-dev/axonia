package dev.axonia.mentoring.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.Builder;

/** A day a pairing plans to meet, with the topics it will take up; {@code held} once a note of the pairing names it. */
@Builder(toBuilder = true)
public record PlannedMeeting(long id, long pairingId, LocalDate on, List<Long> topicIds, String plannedBy,
        Instant plannedAt, boolean held) {
}
