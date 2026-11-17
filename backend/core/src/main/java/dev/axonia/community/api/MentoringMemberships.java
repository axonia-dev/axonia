package dev.axonia.community.api;

/** Whether a member sits in a community taking mentoring, as the mentoring context asks it. */
public interface MentoringMemberships {

    /** Whether the member belongs to a community that is not archived and takes mentoring, in one query. */
    boolean inAMentoringCommunity(String organizationId, String userId);
}
