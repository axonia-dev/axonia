package dev.axonia.learning.application.port.out;

import dev.axonia.learning.domain.LearningCommunity;

public interface CommunityAccess {

    LearningCommunity communityForSlug(String slug);

    boolean isMember(long communityId, String userId);

    /** Org-explicit, for the delivery gate, which judges a recipient off any request. */
    boolean isMember(String organizationId, long communityId, String userId);

    /** The community's name, org-explicit. */
    String nameOf(String organizationId, long communityId);
}
