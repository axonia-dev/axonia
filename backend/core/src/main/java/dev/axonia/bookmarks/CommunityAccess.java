package dev.axonia.bookmarks;

public interface CommunityAccess {

    boolean isMember(long communityId, String userId);

    /** Community identity for a badge, or {@code null} if it can no longer be resolved. */
    CommunitySummary summaryById(long communityId);
}
