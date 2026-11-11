package dev.axonia.health.domain;

import org.junit.jupiter.api.Test;

import dev.axonia.health.api.FeedbackAverages;

import static org.assertj.core.api.Assertions.assertThat;

class LearningDialTest {

    @Test
    void of_averagesEachQuestionToOneDecimalAgainstThePreviousWindow() {

        // given 41 over 5 enjoyed, 50.25 over 6 learned, previously 29 over 4 and 22 over 3
        var current = new FeedbackAverages(7, 5, 41, 6, 50.25, 2, 1);
        var previous = new FeedbackAverages(5, 4, 29, 3, 22, 1, 0);

        // when
        var actual = LearningDial.of(current, previous);

        // then 8.375 rounds half up to 8.4, 7.333 down to 7.3
        assertThat(actual).isEqualTo(LearningDial.builder().enjoyed(8.2).learned(8.4).answers(7).enjoyedAnswers(5)
                .learnedAnswers(6).sessions(2).sessionsTooFew(1).previousEnjoyed(7.3).previousLearned(7.3)
                .status(Status.of(StatusCode.LEARNING_A_LOT)).build());
    }

    @Test
    void of_readsTheStatusFromTheRoundedAverage() {

        // given 39.9 over 5 is 7.98, shown as 8.0
        var current = new FeedbackAverages(5, 5, 40, 5, 39.9, 1, 0);

        // when
        var actual = LearningDial.of(current, FeedbackAverages.NONE);

        // then
        assertThat(actual.learned()).isEqualTo(8.0);
        assertThat(actual.status()).isEqualTo(Status.of(StatusCode.LEARNING_A_LOT));
    }

    @Test
    void empty_hasNoAveragesAndIsTooFew() {

        // when
        var actual = LearningDial.empty();

        // then
        assertThat(actual).isEqualTo(LearningDial.builder().status(Status.of(StatusCode.LEARNING_TOO_FEW)).build());
    }
}
