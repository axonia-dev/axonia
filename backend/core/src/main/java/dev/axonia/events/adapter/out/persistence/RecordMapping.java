package dev.axonia.events.adapter.out.persistence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Maps a child collection between an entity and its domain record, keeping a missing collection missing. */
final class RecordMapping {

    private RecordMapping() {
    }

    static <S, T> List<T> list(List<S> source, Function<S, T> map) {

        return source == null ? null : source.stream().map(map).collect(Collectors.toCollection(ArrayList::new));
    }

    static <S, T> Set<T> set(Set<S> source, Function<S, T> map) {

        return source == null ? null : source.stream().map(map).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
