package dev.axonia.knowledge.application.port.out;

import dev.axonia.community.api.ContributionPolicy;
import dev.axonia.community.api.MemberMatch;

public interface CommunityAccess {

    long communityIdForSlug(String slug);

    boolean isMember(long communityId, String userId);

    boolean isOrganizer(long communityId, String userId);

    /**
     * Whether the caller may say that an artifact is somebody else's work: an organizer, or a member where the
     * community opened its Credit contributions axis. The delegation belongs to {@code community}; this module only
     * enforces it.
     */
    boolean canCreditContributions(long communityId, String userId);

    /**
     * The community's contribution axes, notably {@code canManageSchedule} - the floor for adding a resource before a
     * session is held.
     */
    ContributionPolicy contributionPolicy(long communityId);

    /**
     * Resolves a free-text person reference - a display name, an email, or a user id, as a tool being migrated away
     * from would spell it - to somebody in the organization, members of the community first, so an artifact's
     * contributor can be named rather than guessed. Crediting somebody makes them no member.
     *
     * <p>
     * The resolution belongs to {@code community}, which owns the roster: an address is never synthesized from a name,
     * because a wrong guess silently credits one person's work to another and nobody would ever notice. A reference
     * that does not resolve to exactly one person credits nobody.
     */
    MemberMatch resolvePerson(long communityId, String query);

    /**
     * Whether a reference names this user exactly, by display name or email. Reads only that user's own directory row,
     * never the roster, so it cannot be used to probe other members.
     */
    boolean identifiesExactly(String userId, String reference);
}
