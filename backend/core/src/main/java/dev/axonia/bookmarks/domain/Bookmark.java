package dev.axonia.bookmarks.domain;

import java.time.Instant;

import lombok.Builder;

/** One member's save of an artifact or a session, with whether it helped them. */
@Builder(toBuilder = true)
public record Bookmark(Long id, String organizationId, Long communityId, SaveableType saveableType, Long saveableId,
        String memberUserId, String sourceAuthorUserId, Boolean helped, Instant helpedAt, Instant createdAt,
        Instant updatedAt, Long version) {
}
