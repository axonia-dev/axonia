package dev.axonia.community.api;

/**
 * Published when a community's look & feel — badge image or accent color — changes. For a hub-kind Organization the
 * single community's look IS the hub's public-card look (strict 1:1), so the hub module listens synchronously and keeps
 * the Organization registry card in step within the same transaction. Hero changes never publish: the hero is not part
 * of the public card.
 */
public record CommunityLookChanged(String organizationId, String badgeKey, String accentColor) {
}
