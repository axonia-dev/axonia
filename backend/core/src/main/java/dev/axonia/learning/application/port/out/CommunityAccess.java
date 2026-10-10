package dev.axonia.learning.application.port.out;

import dev.axonia.learning.domain.LearningCommunity;

public interface CommunityAccess {

    LearningCommunity communityForSlug(String slug);

    boolean isMember(long communityId, String userId);
}
