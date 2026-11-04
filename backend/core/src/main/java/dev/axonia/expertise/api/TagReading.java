package dev.axonia.expertise.api;

import java.util.List;

/**
 * How a community's vocabulary reads tags about to be stored on a record: those it never carried, which the write
 * creates, and those a curator merged into another tag, which are stored as given but read as their canonical.
 */
public record TagReading(List<String> created, List<Merged> merged) {

    public TagReading {
        created = List.copyOf(created);
        merged = List.copyOf(merged);
    }

    /** A tag merged into {@code canonical}. */
    public record Merged(String tag, String canonical) {
    }
}
