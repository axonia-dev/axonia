package dev.axonia.recognition;

import lombok.Builder;

/**
 * Application event published when a love is removed from a recognized contribution. Counterpart to
 * {@link ContributionLoved}; consuming modules decrement their denormalized counts in response.
 */
@Builder
public record ContributionUnloved(ContributionType contributionType, Long contributionId, Long communityId) {
}
