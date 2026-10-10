package dev.axonia.recognition.domain;

import java.time.Instant;

import dev.axonia.recognition.api.ContributionType;
import lombok.Builder;

@Builder
public record Love(Long id, ContributionType contributionType, Long contributionId, Long communityId,
        String loverUserId, Instant createdAt) {
}
