package dev.axonia.recognition;

import java.time.Instant;
import java.util.List;

import lombok.Builder;

/**
 * The authoritative attribution facts of a contribution, resolved from its owning bounded context. Recognition trusts
 * these values, never the client's.
 */
@Builder
public record ContributionSource(Long communityId, List<String> contributorUserIds, String title, Instant occurredAt) {
}
