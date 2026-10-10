package dev.axonia.community.api;

/**
 * A member of a community, as the roster shows them.
 *
 * <p>
 * No email: an email is the field that turns a roster into a phishing list, and a caller that already holds an email
 * does not need the roster, because {@link MemberDirectory#resolve} matches an email exactly.
 */
public record CommunityMember(String userId, String displayName) {
}
