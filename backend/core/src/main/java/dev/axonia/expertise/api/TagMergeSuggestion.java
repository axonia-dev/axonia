package dev.axonia.expertise.api;

import java.util.List;

/**
 * Two tags whose names are alike enough to be one area, with what each is holding up, so whoever acts on it can tell a
 * real duplicate from two distinct things and merge the rarer spelling into the more used one.
 */
public record TagMergeSuggestion(Side tagA, Side tagB, double score) {

    /** One tag of the pair: the spellings already merged into it, who can be asked about it, and what carries it. */
    public record Side(String tag, List<String> variants, int peopleCount, TagUsageReport usage) {

        public Side {
            variants = List.copyOf(variants);
        }
    }
}
