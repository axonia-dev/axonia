package dev.axonia.knowledge.internal;

import org.springframework.stereotype.Component;

import dev.axonia.community.CommunityCapabilities;
import dev.axonia.community.CommunityPolicyService;
import dev.axonia.community.CommunityService;
import dev.axonia.community.ContributionPolicy;
import dev.axonia.community.MemberDirectory;
import dev.axonia.community.MemberMatch;
import dev.axonia.community.MembershipService;
import dev.axonia.knowledge.CommunityAccess;
import lombok.RequiredArgsConstructor;

@Component("knowledgeCommunityAccessAdapter")
@RequiredArgsConstructor
class CommunityAccessAdapter implements CommunityAccess {

    private final CommunityService communityService;
    private final MembershipService membershipService;
    private final MemberDirectory memberDirectory;
    private final CommunityPolicyService communityPolicyService;
    private final CommunityCapabilities communityCapabilities;

    @Override
    public long communityIdForSlug(String slug) {

        return communityService.getBySlug(slug).id();
    }

    @Override
    public boolean isMember(long communityId, String userId) {

        return membershipService.isMember(communityId, userId);
    }

    @Override
    public boolean isOrganizer(long communityId, String userId) {

        return membershipService.isOrganizer(communityId, userId);
    }

    // This method is identical to the one in events' adapter, and so are the three above it. CPD's longest common
    // run across the two adapters is 90 tokens against a 100-token floor, and what keeps it under is the
    // events-only methods sitting between them there. contributionPolicy is kept below this one rather than beside
    // isOrganizer for the same reason: beside isOrganizer it would extend this run past the floor, since events'
    // adapter also carries isOrganizer immediately followed by contributionPolicy.
    @Override
    public MemberMatch resolveMember(long communityId, String query) {

        return memberDirectory.resolve(communityId, query);
    }

    @Override
    public ContributionPolicy contributionPolicy(long communityId) {

        return communityPolicyService.getContributionPolicyById(communityId);
    }

    @Override
    public boolean canCreditContributions(long communityId, String userId) {

        return communityCapabilities.canCreditContributions(communityId, userId);
    }

    @Override
    public boolean identifiesExactly(String userId, String reference) {

        return memberDirectory.identifiesExactly(userId, reference);
    }
}
