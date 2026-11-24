package dev.axonia.mentoring.adapter.out.community;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.axonia.community.api.Communities;
import dev.axonia.community.api.Community;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.Memberships;
import dev.axonia.community.api.MentoringMemberships;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.MentoringGuild;
import lombok.RequiredArgsConstructor;

@Component("mentoringCommunityAccessAdapter")
@RequiredArgsConstructor
class CommunityAccessAdapter implements CommunityAccess {

    private final Communities communityService;
    private final Memberships membershipService;
    private final MentoringMemberships mentoringMemberships;

    @Override
    public MentoringCommunity communityForSlug(String slug) {

        return of(communityService.getBySlug(slug));
    }

    @Override
    public MentoringCommunity communityById(long communityId) {

        return of(communityService.getById(communityId));
    }

    @Override
    public MentoringGuild guildOf(String org, long communityId) {

        var identity = communityService.identityById(org, communityId);
        return new MentoringGuild(identity.slug(), identity.name());
    }

    @Override
    public boolean inAMentoringGuild(String org, String userId) {

        return mentoringMemberships.inAMentoringCommunity(org, userId);
    }

    @Override
    public List<Long> mentoringCommunityIdsIn(String org) {

        return mentoringMemberships.mentoringCommunityIds(org);
    }

    @Override
    public List<Long> communityIdsOf(String org, String userId) {

        return membershipService.communityIdsOf(org, userId);
    }

    @Override
    public Map<Long, String> slugsOf(Collection<Long> communityIds) {

        return communityService.identitiesByIds(communityIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().slug()));
    }

    @Override
    public boolean isMember(long communityId, String userId) {

        return membershipService.isMember(communityId, userId);
    }

    @Override
    public Set<String> memberIdsOf(String org, long communityId) {

        return Set.copyOf(membershipService.memberUserIds(org, communityId));
    }

    @Override
    public boolean isMember(String org, long communityId, String userId) {

        return membershipService.isMember(org, communityId, userId);
    }

    private static MentoringCommunity of(Community community) {

        return new MentoringCommunity(community.id(), community.slug(), community.name(),
                community.status() == CommunityStatus.ARCHIVED, community.mentoringEnabled());
    }
}
