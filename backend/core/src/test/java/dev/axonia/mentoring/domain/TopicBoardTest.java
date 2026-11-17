package dev.axonia.mentoring.domain;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.shared.api.error.ValidationException;

import static dev.axonia.mentoring.domain.TopicState.DONE;
import static dev.axonia.mentoring.domain.TopicState.IN_PROGRESS;
import static dev.axonia.mentoring.domain.TopicState.TODO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopicBoardTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T09:00:00Z");

    private static Topic topic(long id, TopicState state, int position) {

        return Topic.builder().id(id).pairingId(90L).label("Topic " + id).state(state).position(position)
                .createdAt(CREATED).build();
    }

    @Test
    void movingWithinAColumnShiftsTheOthers() {

        // given
        var a = topic(1L, TODO, 0);
        var b = topic(2L, TODO, 1);
        var c = topic(3L, TODO, 2);

        // when
        var actual = TopicBoard.place(List.of(a, b, c), 3L, TODO, 0);

        // then
        assertThat(actual).containsExactly(topic(3L, TODO, 0), topic(1L, TODO, 1), topic(2L, TODO, 2));
    }

    @Test
    void movingAcrossColumnsClosesTheGapAndOpensASlot() {

        // given
        var board = List.of(topic(1L, TODO, 0), topic(2L, TODO, 1), topic(3L, TODO, 2), topic(4L, DONE, 0),
                topic(5L, DONE, 1), topic(6L, IN_PROGRESS, 0));

        // when
        var actual = TopicBoard.place(board, 2L, DONE, 1);

        // then
        assertThat(actual).containsExactly(topic(2L, DONE, 1), topic(5L, DONE, 2), topic(3L, TODO, 1));
    }

    @Test
    void aPositionPastTheEndGoesLast() {

        // given
        var board = List.of(topic(1L, TODO, 0), topic(2L, IN_PROGRESS, 0));

        // when
        var actual = TopicBoard.place(board, 1L, IN_PROGRESS, Integer.MAX_VALUE);

        // then
        assertThat(actual).containsExactly(topic(1L, IN_PROGRESS, 1));
    }

    @Test
    void placingATopicWhereItIsChangesNothing() {

        // given
        var board = List.of(topic(1L, TODO, 0), topic(2L, TODO, 1));

        // when
        var actual = TopicBoard.place(board, 2L, TODO, 1);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void aNegativePositionIsRefused() {

        // given
        var board = List.of(topic(1L, TODO, 0));

        // when / then
        assertThatThrownBy(() -> TopicBoard.place(board, 1L, DONE, -1)).isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "field_invalid");
    }

    @Test
    void removingATopicClosesItsGap() {

        // given
        var board = List.of(topic(1L, TODO, 0), topic(2L, TODO, 1), topic(3L, TODO, 2), topic(4L, DONE, 0));

        // when
        var actual = TopicBoard.without(board, 1L);

        // then
        assertThat(actual).containsExactly(topic(2L, TODO, 0), topic(3L, TODO, 1));
    }

    @Test
    void trailOrderIsDoneThenInProgressThenToDo() {

        // given
        var board = List.of(topic(1L, TODO, 0), topic(2L, IN_PROGRESS, 1), topic(3L, DONE, 1),
                topic(4L, IN_PROGRESS, 0), topic(5L, DONE, 0));

        // when
        var actual = TopicBoard.trailOrder(board);

        // then
        assertThat(actual).extracting(Topic::id).containsExactly(5L, 3L, 4L, 2L, 1L);
    }

    @Test
    void boardOrderIsToDoThenInProgressThenDone() {

        // given
        var board = List.of(topic(1L, DONE, 0), topic(2L, IN_PROGRESS, 0), topic(3L, TODO, 1), topic(4L, TODO, 0));

        // when
        var actual = TopicBoard.boardOrder(board);

        // then
        assertThat(actual).extracting(Topic::id).containsExactly(4L, 3L, 2L, 1L);
    }
}
