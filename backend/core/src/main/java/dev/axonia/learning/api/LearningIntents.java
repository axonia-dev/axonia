package dev.axonia.learning.api;

import java.util.List;

/**
 * The areas members want to learn, one community at a time. Intents are stored as typed and read through the
 * community's merges. The caller is always the learner, so nobody adds for anybody else.
 */
public interface LearningIntents {

    /** Keeps an intent a choice rather than a keyword list. */
    int INTENT_LIMIT = 10;

    /**
     * Adds the area to the caller's, stored as typed. Adding an area already held, under any spelling the community
     * reads as it, returns the first intent. The cap counts areas, not spellings. An archived community takes no new
     * intent.
     */
    HeldArea add(String slug, String rawTag);

    /**
     * Removes the area from the caller's, under every spelling they held it by, and answers the area as the community
     * reads it. Still open in an archived community, so nobody is stuck with a statement they no longer make.
     */
    String remove(String slug, String rawTag);

    /** The caller's areas when they are a member, and what the community's current members want, for anyone. */
    LearningPage page(String slug);

    /** The current members who want the area the tag names in this community, earliest first. */
    List<Learner> learners(String slug, String rawTag);
}
