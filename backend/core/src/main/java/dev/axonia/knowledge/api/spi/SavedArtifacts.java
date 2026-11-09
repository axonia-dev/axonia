package dev.axonia.knowledge.api.spi;

import java.util.Set;

/**
 * The artifacts a member saved in one community, so the library can narrow to them. Declared here and implemented by
 * {@code bookmarks}, which already depends on {@code knowledge}: the reverse edge would close a module cycle.
 */
public interface SavedArtifacts {

    Set<Long> savedIn(String organizationId, String memberUserId, long communityId);
}
