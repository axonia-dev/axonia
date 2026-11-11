package dev.axonia.knowledge.api.spi;

import java.util.Set;

/**
 * Members' saves of the library's artifacts: the ones a member saved in one community, so the library can narrow to
 * them, and letting go of every save of an artifact that is removed. Declared here and implemented by
 * {@code bookmarks}, which already depends on {@code knowledge}: the reverse edge would close a module cycle.
 */
public interface SavedArtifacts {

    Set<Long> savedIn(String organizationId, String memberUserId, long communityId);

    /** A removed artifact is nothing to come back to, so no member keeps it saved. */
    void forget(String organizationId, long artifactId);
}
