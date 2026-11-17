package dev.axonia.mentoring.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.axonia.shared.api.error.ValidationException;

/**
 * A pairing's topics on the board: three columns, each numbered densely from 0. A move or a removal renumbers the
 * columns it touches and answers only the topics whose state or position changed.
 */
public final class TopicBoard {

    private static final Map<TopicState, Integer> TRAIL_RANK = Map.of(TopicState.DONE, 0, TopicState.IN_PROGRESS, 1,
            TopicState.TODO, 2);

    private TopicBoard() {
    }

    /** A position past the column's end places the topic last. */
    public static List<Topic> place(List<Topic> topics, long topicId, TopicState to, int position) {

        if (position < 0) {
            throw new ValidationException("field_invalid", "A position is zero or more");
        }
        var moving = topics.stream().filter(t -> t.id() == topicId).findFirst().orElseThrow();
        var target = column(topics, to, topicId);
        target.add(Math.min(position, target.size()), moving.toBuilder().state(to).build());
        var renumbered = new ArrayList<>(numbered(target));
        if (moving.state() != to) {
            renumbered.addAll(numbered(column(topics, moving.state(), topicId)));
        }
        return changed(topics, renumbered);
    }

    /** The rest of the removed topic's column, renumbered to close its gap. */
    public static List<Topic> without(List<Topic> topics, long topicId) {

        var removed = topics.stream().filter(t -> t.id() == topicId).findFirst().orElseThrow();
        return changed(topics, numbered(column(topics, removed.state(), topicId)));
    }

    /** Done first, then In progress, then To do, each in its board order. */
    public static List<Topic> trailOrder(List<Topic> topics) {

        return topics.stream().sorted(
                Comparator.<Topic>comparingInt(t -> TRAIL_RANK.get(t.state())).thenComparingInt(Topic::position))
                .toList();
    }

    /** To do, then In progress, then Done, each in its board order. */
    public static List<Topic> boardOrder(List<Topic> topics) {

        return topics.stream().sorted(Comparator.comparing(Topic::state).thenComparingInt(Topic::position)).toList();
    }

    private static List<Topic> column(List<Topic> topics, TopicState state, long except) {

        return topics.stream().filter(t -> t.state() == state && t.id() != except)
                .sorted(Comparator.comparingInt(Topic::position)).collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<Topic> numbered(List<Topic> column) {

        var numbered = new ArrayList<Topic>(column.size());
        for (var slot = 0; slot < column.size(); slot++) {
            numbered.add(column.get(slot).toBuilder().position(slot).build());
        }
        return numbered;
    }

    private static List<Topic> changed(List<Topic> before, List<Topic> after) {

        var was = before.stream().collect(Collectors.toMap(Topic::id, Function.identity()));
        return after.stream().filter(t -> !t.equals(was.get(t.id()))).toList();
    }
}
