package dev.axonia.mentoring.domain;

import java.time.Instant;

import lombok.Builder;

/**
 * Two members in a mentoring; a side is null once erasure has cleared it. {@code id} is null until stored.
 * {@code showing} says whether the community sees the pairing.
 */
@Builder(toBuilder = true)
public record Pairing(Long id, long communityId, String mentorUserId, String menteeUserId, Instant startedAt,
        Instant endedAt, EndReason endReason, Showing showing) {
}
