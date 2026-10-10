package dev.axonia.community.api;

/** A community's policy axes, read by id. */
public interface CommunityPolicies {

    ContributionPolicy getContributionPolicyById(long communityId);

    TopicManagementPolicy getTopicManagementPolicy(long communityId);

    /** Org-explicit, for a caller with no request to read the Organization from. */
    CommunicationSettings getCommunicationSettingsById(String organizationId, long communityId);
}
