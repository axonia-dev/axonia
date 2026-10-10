package dev.axonia.events.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The one shape both {@link TopicMapper} and {@link ActionItemMapper} reduce their own tag-ref rows to: a distinct,
 * alphabetically sorted list of tag names. Both entities store their tags in a {@code Set}, so insertion order is not
 * persisted and only a total order keeps the stored tags, the read model and any published event saying the same thing.
 */
final class TagRefs {

    private TagRefs() {
    }

    static <T> List<String> sorted(Set<T> refs, Function<T, String> tag) {

        return Objects.requireNonNullElse(refs, Set.<T>of()).stream().map(tag).distinct().sorted().toList();
    }
}
