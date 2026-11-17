package dev.axonia.mentoring.application.port.out;

import java.util.Set;

import dev.axonia.expertise.api.CommunityAreas;

/** The guild's vocabulary, which the expertise context owns: a topic links to an area when the guild carries it. */
public interface Vocabulary {

    /** Held shared until the transaction ends, so no curator rewrites an area a topic is about to link to. */
    void shareLock(String org, long communityId);

    CommunityAreas areasOf(long communityId);

    /** The canonical areas the guild carries where its members see them. Needs the caller's membership. */
    Set<String> areasIn(String slug);

    /** The name a label is stored under in a vocabulary; blank when nothing of it is a name. */
    String normalized(String label);
}
