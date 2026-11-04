package dev.axonia.community.api;

/**
 * Somebody in the organization who can be credited from a community: a presenter, a contributor, an assignee. Whether
 * they are a member of it is said rather than required, because crediting somebody makes them nothing more than
 * credited. Carries no email, for the same reason {@link CommunityMember} does not.
 */
public record CreditablePerson(String userId, String displayName, boolean member) {
}
