package dev.axonia.expertise.api;

import java.util.List;

/**
 * One tag of a community's curated vocabulary: its canonical name, every spelling merged into it, how many people can
 * be asked about it, and the named area it belongs to, {@code null} when it belongs to none.
 */
public record VocabularyTag(String tag, List<String> variants, int peopleCount, NamedAreaRef namedArea) {

    public VocabularyTag {
        variants = List.copyOf(variants);
    }

    /** A tag the vocabulary carries no merge, count or named area for. */
    public static VocabularyTag bare(String tag) {

        return new VocabularyTag(tag, List.of(), 0, null);
    }

    /** Which named area a tag belongs to, without the other tags in it. */
    public record NamedAreaRef(long id, String name) {
    }
}
