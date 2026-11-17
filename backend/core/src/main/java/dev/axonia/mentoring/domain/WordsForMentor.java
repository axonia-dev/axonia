package dev.axonia.mentoring.domain;

import java.time.Instant;

import lombok.Builder;

@Builder
public record WordsForMentor(long pairingId, long communityId, String menteeUserId, String body, Instant writtenAt,
        Instant updatedAt, Instant openedAt, Instant startedAt, Instant endedAt) {
}
