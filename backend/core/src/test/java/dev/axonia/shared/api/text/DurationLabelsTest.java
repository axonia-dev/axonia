package dev.axonia.shared.api.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DurationLabelsTest {

    @Test
    void noDurationHasNoLabel() {

        assertThat(DurationLabels.of(null)).isNull();
        assertThat(DurationLabels.of(0)).isNull();
        assertThat(DurationLabels.of(-5)).isNull();
    }

    @Test
    void underAnHourIsInMinutes() {

        assertThat(DurationLabels.of(58)).isEqualTo("58 min");
    }

    @Test
    void wholeHoursDropTheMinutes() {

        assertThat(DurationLabels.of(60)).isEqualTo("1h");
    }

    @Test
    void remainingMinutesArePaddedToTwoDigits() {

        assertThat(DurationLabels.of(64)).isEqualTo("1h 04");
        assertThat(DurationLabels.of(132)).isEqualTo("2h 12");
    }
}
