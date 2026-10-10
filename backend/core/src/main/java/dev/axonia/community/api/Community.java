package dev.axonia.community.api;

import java.time.Instant;

import lombok.Builder;

@Builder
public record Community(Long id, String slug, String name, Long kindId, String kind, String description,
        CommunityStatus status, Long memberCount, String badgeUrl, String heroUrl, String accentColor,
        FocalPoint heroFocus, FocalPoint badgeFocus, Instant createdAt, Instant updatedAt) {
}
