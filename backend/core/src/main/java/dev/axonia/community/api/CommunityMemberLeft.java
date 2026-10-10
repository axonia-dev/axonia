package dev.axonia.community.api;

/**
 * Published when a member leaves a community. For a hub-kind Organization the single community IS the hub (strict 1:1),
 * so the hub module listens and cascades the departure to the org plane - role binding, member count, and the identity
 * provider's organization membership. Enterprise organizations ignore that part: leaving one community there never
 * means leaving the tenant. The expertise module listens for {@code communityId}: a member who left is no longer open
 * to questions there. The learning module listens for it too: a member who left no longer wants to learn anything
 * there.
 */
public record CommunityMemberLeft(String organizationId, long communityId, String userId) {
}
