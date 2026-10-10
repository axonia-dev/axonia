package dev.axonia.expertise.api;

import java.util.Map;

/**
 * What another context needs to keep records named in a community's vocabulary: its lock, its areas, and who can be
 * asked about each. The reads take the caller's organization from the request, so they must not be called off a
 * request, for example from an event listener or a scheduled job. The lock takes the organization explicitly instead.
 */
public interface CommunityVocabulary {

    /**
     * Takes the community's vocabulary lock shared until the caller's transaction ends, so no curator rewrites the
     * names a record is about to be stored under. The organization is the caller's to name: a member's departure can
     * arrive on a path that carries no member token.
     */
    void shareLock(String org, long communityId);

    /** The community's areas, each with every spelling merged into it. */
    CommunityAreas areasOf(long communityId);

    /** Per canonical area, the number of people a search for it would list: the count the areas list shows. */
    Map<String, Integer> canBeAsked(String slug);

    /** The normalized name a record may be stored under, or a rejection of it. */
    String writableName(String raw);
}
