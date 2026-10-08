package dev.axonia.identity;

import lombok.Builder;

/**
 * The minimized batch-directory projection of a co-member: enough to render names, avatars, and org placement, and
 * deliberately nothing more. Email stays on the caller's own {@code /me} profile.
 *
 * <p>
 * {@code memberStatus} makes this projection total rather than partial: an id that resolves to no row is a departed
 * member, not an absent one, so it still comes back - with {@link UserDirectory.MemberStatus#ERASED} and the neutral
 * {@link UserDirectory.DirectoryUser#DELETED_MEMBER_LABEL} - rather than being silently dropped from the batch.
 */
@Builder
public record DirectoryUser(String id, String displayName, String avatarUrl, String department, String businessUnit,
        String team, UserDirectory.MemberStatus memberStatus) {
}
