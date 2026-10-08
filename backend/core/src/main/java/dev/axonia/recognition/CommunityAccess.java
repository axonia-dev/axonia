package dev.axonia.recognition;

import java.util.Collection;
import java.util.Map;

public interface CommunityAccess {

    boolean isMember(long communityId, String userId);

    /**
     * Resolves each community id to its public identity (slug + name). Ids that no longer resolve to a community are
     * omitted from the result, so callers must tolerate missing keys.
     */
    Map<Long, CommunitySummary> summaryById(Collection<Long> communityIds);
}
