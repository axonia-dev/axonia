package dev.axonia.expertise.domain;

/** How one community reads one of the caller's areas, after the caller's privacy choices. */
public record CommunityTier(long communityId, Tier tier) {
}
