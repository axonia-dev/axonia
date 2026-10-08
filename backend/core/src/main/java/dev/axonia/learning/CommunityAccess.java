package dev.axonia.learning;

public interface CommunityAccess {

    LearningCommunity communityForSlug(String slug);

    boolean isMember(long communityId, String userId);
}
