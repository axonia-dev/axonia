package dev.axonia.learning.internal;

import org.springframework.stereotype.Component;

import dev.axonia.community.CommunityService;
import dev.axonia.community.CommunityStatus;
import dev.axonia.community.MembershipService;
import dev.axonia.learning.CommunityAccess;
import dev.axonia.learning.LearningCommunity;
import lombok.RequiredArgsConstructor;

@Component("learningCommunityAccessAdapter")
@RequiredArgsConstructor
class CommunityAccessAdapter implements CommunityAccess {

    private final CommunityService communityService;
    private final MembershipService membershipService;

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
