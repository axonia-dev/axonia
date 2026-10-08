package dev.axonia.onboarding;

import java.util.Optional;

/** What the community context answers about the caller in one community: nothing, or where they stand in it. */
public interface MembershipStanding {

    /** Empty when the caller is not a member of this community, or the community does not resolve. */
    Optional<Standing> standingIn(String slug);

    record Standing(Long communityId, String organizationId, boolean organizer) {
    }
}
