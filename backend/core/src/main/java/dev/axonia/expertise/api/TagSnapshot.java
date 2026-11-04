package dev.axonia.expertise.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tags a community already carries where any member can see them, and its merges, taken before a record is stored
 * so the tags the record ended up with can be read against it afterwards. Members' declarations hidden from the
 * community are left out, so the reading never tells anyone a hidden tag exists.
 */
public record TagSnapshot(Set<String> known, Map<String, String> aliases) {

    public TagSnapshot {
        known = Set.copyOf(known);
        aliases = Map.copyOf(aliases);
    }

    /** How the community reads the normalized tags a record was stored under. */
    public TagReading read(Collection<String> storedTags) {

        var tags = storedTags.stream().distinct().toList();
        var merged = tags.stream().filter(aliases::containsKey).map(tag -> new TagReading.Merged(tag, aliases.get(tag)))
                .toList();
        var created = tags.stream().filter(tag -> !aliases.containsKey(tag) && !known.contains(tag)).toList();
        return new TagReading(created, merged);
    }

    /**
     * Tags a record was stored under: those the community did not carry before, which the write created, and those a
     * curator merged into another tag, which are stored as given but read as their canonical.
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
}
