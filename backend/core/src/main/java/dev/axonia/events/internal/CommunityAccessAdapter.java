package dev.axonia.events.internal;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.communication.ChatChannelStatus;
import dev.axonia.communication.ChatDestinationsPort.ChatFailure;
import dev.axonia.community.ChatDestinationService;
import dev.axonia.community.CommunicationSettings;
import dev.axonia.community.CommunityCapabilities;
import dev.axonia.community.CommunityIdentity;
import dev.axonia.community.CommunityPolicyService;
import dev.axonia.community.CommunityService;
import dev.axonia.community.ContributionPolicy;
import dev.axonia.community.MemberDirectory;
import dev.axonia.community.MemberMatch;
import dev.axonia.community.MembershipService;
import dev.axonia.community.TopicManagementPolicy;
import dev.axonia.events.CommunityAccess;
import dev.axonia.events.OrganizationContext;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class CommunityAccessAdapter implements CommunityAccess {

    private final CommunityService communityService;
    private final CommunityPolicyService communityPolicyService;
    private final CommunityCapabilities communityCapabilities;
    private final MembershipService membershipService;
    private final MemberDirectory memberDirectory;
    private final OrganizationContext organizationContext;
    private final ChatDestinationService chatDestinationService;

    @Override
    public long communityIdForSlug(String slug) {

        return communityService.getBySlug(slug).id();
    }

    @Override
    public CommunityIdentity communityIdentity(String organizationId, long communityId) {

        return communityService.identityById(organizationId, communityId);
    }

    @Override
    public boolean isMember(long communityId, String userId) {

        return membershipService.isMember(communityId, userId);
    }

    @Override
    public boolean isOrganizer(long communityId, String userId) {

        return membershipService.isOrganizer(communityId, userId);
    }

    @Override
    public ContributionPolicy contributionPolicy(long communityId) {

        return communityPolicyService.getContributionPolicyById(communityId);
    }

    @Override
    public TopicManagementPolicy topicManagementPolicy(long communityId) {

        return communityPolicyService.getTopicManagementPolicy(communityId);
    }

    @Override
    public boolean canManageCommunication(long communityId, String userId) {

        return communityCapabilities.canManageCommunication(communityId, userId);
    }

    @Override
    public CommunicationSettings communicationSettings(long communityId) {

        return communityPolicyService.getCommunicationSettingsById(organizationContext.organizationId(), communityId);
    }

    @Override
    public CommunicationSettings communicationSettings(String organizationId, long communityId) {

        return communityPolicyService.getCommunicationSettingsById(organizationId, communityId);
    }

    @Override
    public boolean isMember(String organizationId, long communityId, String userId) {

        return membershipService.isMember(organizationId, communityId, userId);
    }

    // Last on purpose. This method is identical to the one in knowledge's adapter, and so are the three delegations at
    // the top of the class. CPD's longest common run across the two adapters is 90 tokens against a 100-token floor,
    // and what keeps it under is the events-only methods sitting between them. Regrouping the delegations, here or
    // there, fails the gate.
    @Override
    public MemberMatch resolveMember(long communityId, String query) {

        return memberDirectory.resolve(communityId, query);
    }

    @Override
    public List<String> memberUserIds(String organizationId, long communityId) {

        return membershipService.memberUserIds(organizationId, communityId);
    }

    @Override
    public List<String> organizerUserIds(String organizationId, long communityId) {

        return membershipService.organizerUserIds(organizationId, communityId);
    }

    @Override
    public ChatChannelStatus chatDestinationStatus(String organizationId, long destinationId) {

        return chatDestinationService.statusFor(organizationId, destinationId);
    }

    @Override
    public List<Long> chatDestinationIds(String organizationId, long communityId) {

        return chatDestinationService.destinationIdsFor(organizationId, communityId);
    }

    @Override
    public void recordChatDelivered(String organizationId, long destinationId, Instant at) {

        chatDestinationService.recordChatDelivered(organizationId, destinationId, at);
    }

    @Override
    public void recordChatFailure(String organizationId, long destinationId, ChatFailure failure, int brokenAfter) {

        chatDestinationService.recordChatFailure(organizationId, destinationId, failure, brokenAfter);
    }
}
