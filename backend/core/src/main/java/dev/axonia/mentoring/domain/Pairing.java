package dev.axonia.mentoring.domain;

import java.time.Instant;
import java.time.LocalDate;

import lombok.Builder;

/**
 * Two members in a mentoring; a side is null once erasure has cleared it. {@code id} is null until stored. The next
 * meeting is one day, set by either person; the three fields are null together.
 */
@Builder(toBuilder = true)
public record Pairing(Long id, long communityId, String mentorUserId, String menteeUserId, Instant startedAt,
        Instant endedAt, EndReason endReason, LocalDate nextMeetingOn, String nextMeetingSetBy,
        Instant nextMeetingSetAt) {
}
