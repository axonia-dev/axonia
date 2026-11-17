package dev.axonia.mentoring.api;

import java.time.Instant;

import lombok.Builder;

/**
 * What a mentee wrote to the caller as mentor; {@code menteeId} is null once the mentee was erased. {@code writtenAt}
 * is when this version of the words was written, so it changes with every change of the words; {@code openedAt} is when
 * the mentor opened this version, null while it is sealed.
 */
@Builder
public record WordsView(long pairingId, String communitySlug, String communityName, String menteeId, String menteeName,
        String menteeAvatarUrl, Instant startedAt, Instant endedAt, String body, Instant writtenAt, Instant openedAt) {
}
