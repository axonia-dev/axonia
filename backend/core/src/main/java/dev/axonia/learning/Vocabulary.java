package dev.axonia.learning;

import java.util.Map;

import dev.axonia.expertise.CommunityAreas;

/** The community's vocabulary, which the expertise context owns: intents are stored as typed and read through it. */
public interface Vocabulary {

    /**
     * Held shared until the transaction ends, so no curator rewrites the names an add or a removal canonicalizes
     * against.
     */
    void shareLock(String org, long communityId);

    CommunityAreas areasOf(long communityId);

    /** Per canonical area, how many people a search for it would list; an area missing from it has nobody to ask. */
    Map<String, Integer> canBeAsked(String slug);

    /** The normalized name an intent may be stored under, or a rejection of it. */
    String writableName(String raw);
}
