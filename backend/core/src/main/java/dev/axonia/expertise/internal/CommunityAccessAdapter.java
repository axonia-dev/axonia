package dev.axonia.expertise.internal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.community.CommunityCapabilities;
import dev.axonia.community.CommunityService;
import dev.axonia.community.CommunityStatus;
import dev.axonia.community.MembershipService;
import dev.axonia.expertise.CommunityAccess;
import dev.axonia.expertise.CommunityRef;
import dev.axonia.expertise.CommunitySummary;
import dev.axonia.expertise.DeclarableCommunity;
import lombok.RequiredArgsConstructor;

@Component("expertiseCommunityAccessAdapter")
@RequiredArgsConstructor
class CommunityAccessAdapter implements CommunityAccess {

    private final CommunityService communityService;
    private final CommunityCapabilities communityCapabilities;
    private final MembershipService membershipService;

    @Override
    public long communityIdForSlug(String slug) {

        return communityService.getBySlug(slug).id();
    }

    @Override
    public CommunityRef communityForSlug(String slug) {

        var community = communityService.getBySlug(slug);
        return new CommunityRef(community.id(), community.status() == CommunityStatus.ARCHIVED);
    }

    /** A community removed after the signal was recorded simply has no link target, so it is absent from the map. */
    @Override
    public Map<Long, CommunitySummary> summaryById(Collection<Long> communityIds) {

        var distinct = communityIds.stream().distinct().toList();
        var identities = communityService.identitiesByIds(distinct);
        var summaries = new LinkedHashMap<Long, CommunitySummary>();
        for (var id : distinct) {
            var identity = identities.get(id);
            if (identity != null) {
                summaries.put(id, new CommunitySummary(identity.slug(), identity.name()));
            }
        }
        return summaries;
    }

    @Override
    public List<DeclarableCommunity> declarableCommunities(String organizationId, String userId) {

        return membershipService.activeCommunitiesOf(organizationId, userId).stream()
                .map(community -> new DeclarableCommunity(community.id(), community.slug(), community.name())).toList();
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
    public boolean canCurateTags(long communityId, String userId) {

        return communityCapabilities.canCurateTags(communityId, userId);
    }
}
