package dev.axonia.events.application;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dev.axonia.events.domain.SlotPresenter;

/** A slot's presenters as user ids, deduplicated by id since {@link SlotPresenter} has no equality of its own. */
final class SlotPresenters {

    private SlotPresenters() {
    }

    /** The presenters a slot already has, then the speakers a topic brings along, each person once. */
    static List<String> merged(List<String> presenterIds, Collection<String> speakerUserIds) {

        var merged = new LinkedHashSet<>(presenterIds);
        speakerUserIds.stream().sorted().forEach(merged::add);
        return List.copyOf(merged);
    }

    static Set<SlotPresenter> of(List<String> userIds) {

        return userIds.stream().distinct().map(id -> SlotPresenter.builder().userId(id).build())
                .collect(Collectors.toCollection(HashSet::new));
    }
}
