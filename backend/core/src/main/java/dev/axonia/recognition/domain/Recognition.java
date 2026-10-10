package dev.axonia.recognition.domain;

import java.time.Instant;

import dev.axonia.recognition.api.ContributionType;
import lombok.Builder;

/**
 * A contribution as recognition holds it once somebody has loved it: which one, in which community, and the title and
 * moment it was attributed with at the time. Every stored column is carried, so it round-trips exactly.
 */
@Builder(toBuilder = true)
public record Recognition(Long id, String organizationId, ContributionType contributionType, Long contributionId,
        Long communityId, String title, Instant occurredAt, Instant createdAt, Instant updatedAt, Long version) {
}
