package dev.axonia.recognition.domain;

import dev.axonia.recognition.api.ContributionType;
import lombok.Builder;

/**
 * The composite identity of one placed reaction: within which organization, on which contribution, by which member,
 * with which emoji or catalog definition. {@code ReactionRepository#deletePlacement} binds every property through SpEL
 * rather than as six separate parameters, on the same precedent {@code AvatarOverrideUpdate} follows for a targeted
 * repository update.
 */
@Builder
public record ReactionPlacementKey(String organizationId, ContributionType contributionType, Long contributionId,
        String reactorUserId, String emoji, Long definitionId) {
}
