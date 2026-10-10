package dev.axonia.community.api;

/** Whether one caller holds one management axis of a community: an organizer always does, a member where it is open. */
public interface CommunityCapabilities {

    boolean canCurateTags(long communityId, String userId);

    boolean canInviteMembers(long communityId, String userId);

    boolean canManageCommunication(long communityId, String userId);

    boolean canCreditContributions(long communityId, String userId);
}
