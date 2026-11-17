package dev.axonia.mentoring.domain;

import java.time.Instant;

import lombok.Builder;

/** What a pairing works on; {@code area} is the community area it points at, if any. */
@Builder(toBuilder = true)
public record Topic(Long id, long pairingId, String label, String area, TopicState state, int position,
        Instant createdAt) {
}
