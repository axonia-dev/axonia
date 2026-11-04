package dev.axonia.expertise.api;

/**
 * Both tags after a split: the variant standing on its own again, and the canonical it was merged into, {@code null}
 * when the variant was not merged.
 */
public record TagSplit(VocabularyTag variant, VocabularyTag formerCanonical) {
}
