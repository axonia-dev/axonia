package dev.axonia.mentoring.domain;

import java.time.Instant;

import lombok.Builder;

/** A reference hanging off exactly one of a topic or a note. */
@Builder
public record Pointer(Long id, long pairingId, Long topicId, Long noteId, PointerKind kind, Long targetId, String url,
        Instant createdAt) {
}
