package dev.axonia.health.domain;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthSnapshotTest {

    @Test
    void series_isSixEmptyListsWhenNoneWasAssembled() {

        // A null series serializes as `"series": null`, which the frontend's schema rejects
        // outright. The list fields default to empty for the same reason.
        var snapshot = HealthSnapshot.builder().window("90d").build();

        assertThat(snapshot.series()).isEqualTo(HealthSnapshot.Series.empty());
        assertThat(snapshot.series().openings()).isEmpty();
        assertThat(snapshot.series().saves()).isEmpty();
        assertThat(snapshot.heldSessionDates()).isEmpty();
    }

    @Test
    void series_isKeptAsGiven() {

        var series = new HealthSnapshot.Series(List.of(1L), List.of(2L), List.of(3L), List.of(4L), List.of(5L),
                List.of(6L));
        var held = List.of(Instant.parse("2026-04-02T17:00:00Z"));

        var snapshot = HealthSnapshot.builder().window("90d").series(series).heldSessionDates(held).build();

        assertThat(snapshot.series()).isEqualTo(series);
        assertThat(snapshot.heldSessionDates()).isEqualTo(held);
    }
}
