package dev.axonia.shared.api.metrics;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The rules the three meter vocabularies share: what a tag may look like, how a contribution joins, what is missing.
 */
public final class MeterVocabulary {

    private static final Pattern TAG = Pattern.compile("[a-z](?:[a-z0-9]|-(?=[a-z0-9]))*");

    private MeterVocabulary() {
    }

    public static String requireTag(String tag) {

        if (tag == null || !TAG.matcher(tag).matches()) {
            throw new IllegalArgumentException("Not a meter tag: " + tag);
        }
        return tag;
    }

    public static <T> List<T> join(List<T> open, Stream<T> contributed, Function<T, String> tagOf) {

        var all = Stream.concat(open.stream(), contributed).toList();
        var shared = all.stream().collect(Collectors.groupingBy(tagOf, TreeMap::new, Collectors.counting())).entrySet()
                .stream().filter(tag -> tag.getValue() > 1).map(Map.Entry::getKey).toList();
        if (!shared.isEmpty()) {
            throw new IllegalStateException("Two meter vocabulary entries share the tag " + shared);
        }
        return all;
    }

    public static <T, M> M registered(Map<T, M> meters, T entry, String tag) {

        var meter = meters.get(entry);
        if (meter == null) {
            throw new IllegalArgumentException("No meter is registered for " + tag
                    + ": the module that uses it contributes it as a MeterContribution");
        }
        return meter;
    }
}
