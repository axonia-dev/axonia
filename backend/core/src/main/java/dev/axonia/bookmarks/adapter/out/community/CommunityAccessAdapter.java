package dev.axonia.bookmarks.adapter.out.community;

import org.springframework.stereotype.Component;

import dev.axonia.bookmarks.application.port.out.CommunityAccess;
import dev.axonia.bookmarks.domain.CommunitySummary;
import dev.axonia.community.api.Communities;
import dev.axonia.community.api.Community;
import dev.axonia.community.api.Memberships;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

@Component("bookmarksCommunityAccessAdapter")
@RequiredArgsConstructor
class CommunityAccessAdapter implements CommunityAccess {

    private final Communities communityService;
    private final Memberships membershipService;

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
