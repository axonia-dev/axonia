package dev.axonia.bookmarks.application.port.out;

import dev.axonia.bookmarks.domain.CommunitySummary;

public interface CommunityAccess {

    boolean isMember(long communityId, String userId);

    /** Community identity for a badge, or {@code null} if it can no longer be resolved. */
    CommunitySummary summaryById(long communityId);
}
