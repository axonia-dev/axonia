package dev.axonia.health.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class DistributionTest {

    @Test
    void of_noTalks() {

        // when
        var actual = Distribution.of(List.of());

        // then
        assertThat(actual.talks()).isZero();
        assertThat(actual.speakers()).isZero();
        assertThat(actual.speakersForHalf()).isZero();
        assertThat(actual.topShare()).isZero();
        assertThat(actual.shares()).isEmpty();
    }

    @Test
    void of_zeroCountsAreNotSpeakers() {

        // when
        var actual = Distribution.of(List.of(0L, 4L, 0L));

        // then
        assertThat(actual.talks()).isEqualTo(4);
        assertThat(actual.speakers()).isEqualTo(1);
        assertThat(actual.shares()).containsExactly(1.0);
        assertThat(actual.speakersForHalf()).isEqualTo(1);
    }

    @Test
    void speakersForHalf_lopsidedSpread() {

        // given two people give 68% (40+28), the tail the rest
        var counts = List.of(40L, 28L, 12L, 10L, 10L);

        // when
        var actual = Distribution.of(counts);

        // then
        assertThat(actual.speakersForHalf()).isEqualTo(2);
        assertThat(actual.topShare()).isCloseTo(0.40, offset(1e-9));
        assertThat(actual.talks()).isEqualTo(100);
        assertThat(actual.speakers()).isEqualTo(5);
    }

    @Test
    void speakersForHalf_exactlyHalfCountsTheCrosser() {

        // when
        var actual = Distribution.of(List.of(5L, 3L, 2L));

        // then
        assertThat(actual.speakersForHalf()).isEqualTo(1);
    }

    @Test
    void speakersForHalf_allEqualSpread() {

        // when
        var actual = Distribution.of(List.of(5L, 5L, 5L, 5L));

        // then
        assertThat(actual.speakersForHalf()).isEqualTo(2);
        assertThat(actual.topShare()).isCloseTo(0.25, offset(1e-9));
    }

    @Test
    void shares_sortedDescendingAndAnonymous() {

        // when
        var actual = Distribution.of(List.of(1L, 6L, 3L));

        // then
        assertThat(actual.shares()).containsExactly(0.6, 0.3, 0.1);
    }
}
