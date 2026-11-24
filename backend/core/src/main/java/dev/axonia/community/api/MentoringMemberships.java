package dev.axonia.community.api;

import java.util.List;

/** Which communities take mentoring, and whether a member sits in one, as the mentoring context asks it. */
public interface MentoringMemberships {

    /** Whether the member belongs to a community that is not archived and takes mentoring, in one query. */
    boolean inAMentoringCommunity(String organizationId, String userId);

    /** The organization's communities that are not archived and take mentoring, by id. */
    List<Long> mentoringCommunityIds(String organizationId);
}
