package dev.axonia.community.api;

/**
 * Published when a community's display facts — name or description — change. For a hub-kind Organization the single
 * community's name IS the hub's public name (strict 1:1), so the hub module listens synchronously and keeps the
 * Organization registry card and the identity provider's display name in step within the same transaction.
 */
public record CommunityDetailsChanged(String organizationId, Long communityId, String name, String description) {
}
