package dev.axonia.expertise.api;

/**
 * The tables of {@link MemberTag}s another context owns and reaches through {@link MemberTagStores}. A closed set, so
 * no caller can name a table of its own choosing.
 */
public enum MemberTagKind {

    LEARNING_INTENT
}
