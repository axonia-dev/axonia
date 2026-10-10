package dev.axonia.learning.adapter.out.community;

import org.springframework.stereotype.Component;

import dev.axonia.community.api.Communities;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.Memberships;
import dev.axonia.learning.application.port.out.CommunityAccess;
import dev.axonia.learning.domain.LearningCommunity;
import lombok.RequiredArgsConstructor;

@Component("learningCommunityAccessAdapter")
@RequiredArgsConstructor
class CommunityAccessAdapter implements CommunityAccess {

    private final Communities communityService;
    private final Memberships membershipService;

    @Override
    public LearningCommunity communityForSlug(String slug) {

        var community = communityService.getBySlug(slug);
        return new LearningCommunity(community.id(), community.status() == CommunityStatus.ARCHIVED);
    }

    @Override
    public boolean isMember(long communityId, String userId) {

        return membershipService.isMember(communityId, userId);
    }
}
