package dev.axonia.bookmarks.internal;

import org.springframework.stereotype.Component;

import dev.axonia.NotFoundException;
import dev.axonia.bookmarks.CommunityAccess;
import dev.axonia.bookmarks.CommunitySummary;
import dev.axonia.community.Community;
import dev.axonia.community.CommunityService;
import dev.axonia.community.MembershipService;
import lombok.RequiredArgsConstructor;

@Component("bookmarksCommunityAccessAdapter")
@RequiredArgsConstructor
class CommunityAccessAdapter implements CommunityAccess {

    private final CommunityService communityService;
    private final MembershipService membershipService;

    @Override
    public boolean isMember(long communityId, String userId) {
        return membershipService.isMember(communityId, userId);
    }

    @Override
    public CommunitySummary summaryById(long communityId) {
        try {
            Community c = communityService.getById(communityId);
            return new CommunitySummary(c.id(), c.slug(), c.name(), c.accentColor());
        } catch (NotFoundException e) {
            // Left/deleted community — the save is still shown, just without a badge.
            return null;
        }
    }
}
