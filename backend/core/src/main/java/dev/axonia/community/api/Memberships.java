package dev.axonia.community.api;

import java.util.List;

/** Who belongs to which community, as another context asks it. */
public interface Memberships {

    Membership join(String slug);

    /** The caller's own membership of this community, or 404 when they do not belong. */
    Membership myMembership(String slug);

    /** The member's communities that are not archived, ordered by name ignoring case. */
    List<MemberCommunity> activeCommunitiesOf(String organizationId, String userId);

    /** Community ids the given member belongs to within an Organization. */
    List<Long> communityIdsOf(String organizationId, String userId);

    /** Removes a member on the account-erasure orchestrator's behalf, without the last-organizer guard. */
    void leaveForErasure(String organizationId, Long communityId, String userId);

    boolean isMember(Long communityId, String userId);

    /** Org-explicit, for a caller with no request to read the Organization from. */
    boolean isMember(String organizationId, Long communityId, String userId);

    boolean isOrganizer(Long communityId, String userId);

    /** Every member of the community, as bare user ids; org-explicit. */
    List<String> memberUserIds(String organizationId, Long communityId);

    /** The community's organizers, as bare user ids; org-explicit. */
    List<String> organizerUserIds(String organizationId, Long communityId);
}
