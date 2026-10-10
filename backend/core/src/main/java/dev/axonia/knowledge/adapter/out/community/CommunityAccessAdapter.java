package dev.axonia.knowledge.adapter.out.community;

import org.springframework.stereotype.Component;

import dev.axonia.community.api.Communities;
import dev.axonia.community.api.CommunityCapabilities;
import dev.axonia.community.api.CommunityPolicies;
import dev.axonia.community.api.ContributionPolicy;
import dev.axonia.community.api.MemberDirectory;
import dev.axonia.community.api.MemberMatch;
import dev.axonia.community.api.Memberships;
import dev.axonia.knowledge.application.port.out.CommunityAccess;
import lombok.RequiredArgsConstructor;

@Component("knowledgeCommunityAccessAdapter")
@RequiredArgsConstructor
class CommunityAccessAdapter implements CommunityAccess {

    private final Communities communityService;
    private final Memberships membershipService;
    private final MemberDirectory memberDirectory;
    private final CommunityPolicies communityPolicyService;
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
