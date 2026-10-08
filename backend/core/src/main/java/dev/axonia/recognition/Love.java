package dev.axonia.recognition;

import java.time.Instant;

import lombok.Builder;

@Builder
public record Love(Long id, ContributionType contributionType, Long contributionId, Long communityId,
        String loverUserId, Instant createdAt) {
}
