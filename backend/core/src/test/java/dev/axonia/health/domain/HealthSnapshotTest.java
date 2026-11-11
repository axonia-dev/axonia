package dev.axonia.health.domain;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthSnapshotTest {

    @Test
    void series_isThreeEmptyListsWhenNoneWasAssembled() {

        // A null series serializes as `"series": null`, which the frontend's schema rejects
        // outright. The list fields default to empty for the same reason.
        var snapshot = HealthSnapshot.builder().window("90d").build();

        assertThat(snapshot.series()).isEqualTo(HealthSnapshot.Series.empty());
        assertThat(snapshot.series().talks()).isEmpty();
        assertThat(snapshot.series().people()).isEmpty();
        assertThat(snapshot.series().joins()).isEmpty();
        assertThat(snapshot.heldSessionDates()).isEmpty();
    }

    @Test
    void series_isKeptAsGiven() {

        var series = new HealthSnapshot.Series(List.of(1L), List.of(2L), List.of(3L));
        var held = List.of(Instant.parse("2026-04-02T17:00:00Z"));

        var snapshot = HealthSnapshot.builder().window("90d").series(series).heldSessionDates(held).build();

        assertThat(snapshot.series()).isEqualTo(series);
        assertThat(snapshot.heldSessionDates()).isEqualTo(held);
    }

    @Test
    void stage_nullDistributionIsEmpty() {

        var stage = HealthSnapshot.StageDial.builder().build();

        assertThat(stage.distribution()).isEmpty();
    }

    @Test
    void learning_isTheEmptyDialWhenNoneWasAssembled() {

        // when
        var snapshot = HealthSnapshot.builder().window("90d").build();

        // then
        assertThat(snapshot.learning()).isEqualTo(LearningDial.empty());
    }

    @Test
    void learning_isKeptAsGiven() {

        // given
        var learning = LearningDial.builder().learned(8.1).learnedAnswers(6)
                .status(Status.of(StatusCode.LEARNING_A_LOT)).build();

        // when
        var snapshot = HealthSnapshot.builder().window("90d").learning(learning).build();

        // then
        assertThat(snapshot.learning()).isEqualTo(learning);
    }
}
