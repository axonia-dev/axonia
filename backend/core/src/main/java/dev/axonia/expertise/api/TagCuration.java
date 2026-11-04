package dev.axonia.expertise.api;

import java.util.List;

/**
 * A curator's hold on a community's tags, for callers outside the expertise module. Every call requires curation access
 * to the community, and every write answers with the state it left, so a caller can act on it next.
 */
public interface TagCuration {

    /** Every tag the community carries, folded onto its canonical, with the named area it belongs to. */
    List<VocabularyTag> vocabulary(String slug);

    /** The pairs of tags alike enough to be one, minus the pairs a curator dismissed. */
    List<TagMergeSuggestion> suggestions(String slug);

    /**
     * Merges every variant into the canonical, in one go or not at all. Content keeps the spelling it was tagged with,
     * so a merge is undone by {@link #split}.
     *
     * @return the canonical with every spelling now merged into it
     */
    VocabularyTag merge(String slug, List<String> variants, String canonical);

    /** Unmerges the variant from the canonical it was merged into. */
    TagSplit split(String slug, String variant);

    /**
     * Renames a standalone tag on all the community's content. A name another tag already carries folds the two into
     * one.
     *
     * @return the tag under its new name
     */
    VocabularyTag rename(String slug, String from, String to);

    /** Parks the pair so it is no longer suggested as a merge. */
    void dismiss(String slug, String tagA, String tagB);

    /** Suggests the pair again. */
    void undismiss(String slug, String tagA, String tagB);

    /** What the tag is holding up across the community's content and its members' declarations. */
    TagUsageReport usage(String slug, String tag);

    /** Strips the tag from every piece of the community's content and from the expertise built on it. */
    void delete(String slug, String tag);
}
