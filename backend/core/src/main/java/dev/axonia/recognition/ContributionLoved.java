package dev.axonia.recognition;

import lombok.Builder;

/**
 * Application event published when a love is added to a recognized contribution. Carries enough to let consuming
 * modules (e.g. knowledge) maintain their own denormalized counts without reaching into recognition's internals.
 */
@Builder
public record ContributionLoved(ContributionType contributionType, Long contributionId, Long communityId) {
}
