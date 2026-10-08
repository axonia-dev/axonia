package dev.axonia.community;

import java.time.Instant;

import lombok.Builder;

@Builder
public record Membership(Long id, Long communityId, String userId, CommunityRole role, Instant joinedAt,
        Instant updatedAt) {
}
