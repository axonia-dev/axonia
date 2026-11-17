package dev.axonia.mentoring.domain;

import java.time.Instant;

import lombok.Builder;

/**
 * The mentee's parting words to the mentor; {@code updatedAt} is null until the first change, and {@code openedAt} is
 * null until the mentor opens them, and again after each change.
 */
@Builder
public record Words(long pairingId, String body, Instant writtenAt, Instant updatedAt, Instant openedAt) {
}
