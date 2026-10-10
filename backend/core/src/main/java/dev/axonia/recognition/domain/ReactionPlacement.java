package dev.axonia.recognition.domain;

import java.time.Instant;

import dev.axonia.recognition.api.ContributionType;
import lombok.Builder;

/** One reaction a member placed on a contribution: a standard emoji or a catalog definition, never both. */
@Builder(toBuilder = true)
public record ReactionPlacement(Long id, String organizationId, Long communityId, ContributionType contributionType,
        Long contributionId, String reactorUserId, String emoji, Long definitionId, Instant createdAt,
        Instant updatedAt, Long version) {
}
