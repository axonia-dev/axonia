package dev.axonia.expertise.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import dev.axonia.expertise.domain.CommunityRef;
import dev.axonia.expertise.domain.CommunitySummary;
import dev.axonia.expertise.domain.DeclarableCommunity;

public interface CommunityAccess {

    long communityIdForSlug(String slug);

    CommunityRef communityForSlug(String slug);

    /**
     * Resolves each community id to its public identity (slug + name). Ids that no longer resolve to a community are
     * omitted from the result, so callers must tolerate missing keys.
     */
    Map<Long, CommunitySummary> summaryById(Collection<Long> communityIds);

    /** The user's current memberships in active communities, sorted by name ignoring case. */
    List<DeclarableCommunity> declarableCommunities(String organizationId, String userId);

    boolean isMember(long communityId, String userId);

    boolean isOrganizer(long communityId, String userId);

    boolean canCurateTags(long communityId, String userId);
}
