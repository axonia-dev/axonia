package dev.axonia.expertise.adapter.out.community;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.community.api.Communities;
import dev.axonia.community.api.CommunityCapabilities;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.Memberships;
import dev.axonia.expertise.application.port.out.CommunityAccess;
import dev.axonia.expertise.domain.CommunityRef;
import dev.axonia.expertise.domain.CommunitySummary;
import dev.axonia.expertise.domain.DeclarableCommunity;
import lombok.RequiredArgsConstructor;

@Component("expertiseCommunityAccessAdapter")
@RequiredArgsConstructor
class CommunityAccessAdapter implements CommunityAccess {

    private final Communities communityService;
    private final CommunityCapabilities communityCapabilities;
    private final Memberships membershipService;

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
