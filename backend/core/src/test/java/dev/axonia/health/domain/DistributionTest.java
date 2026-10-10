package dev.axonia.health.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class DistributionTest {

    @Test
    void of_noContributions() {

        // when
        var actual = Distribution.of(List.of());

        // then "no activity" — itself a fading signal
        assertThat(actual.busFactor()).isZero();
        assertThat(actual.top3Share()).isZero();
        assertThat(actual.shares()).isEmpty();
    }

    @Test
    void of_zeroCountsAreNotContributors() {

        // when
        var actual = Distribution.of(List.of(0L, 4L, 0L));

        // then
        assertThat(actual.shares()).containsExactly(1.0);
        assertThat(actual.busFactor()).isEqualTo(1);
    }

    @Test
    void busFactor_singleContributorIsFragile() {

        // when
        var actual = Distribution.of(List.of(7L));

        // then
        assertThat(actual.busFactor()).isEqualTo(1);
    }

    @Test
    void busFactor_lopsidedSpread() {

        // given two people carry 68% (40+28), the tail the rest
        var counts = List.of(40L, 28L, 12L, 10L, 10L);

        // when
        var actual = Distribution.of(counts);

        // then the top contributor alone is 40% — second crosses 50%
        assertThat(actual.busFactor()).isEqualTo(2);
        assertThat(actual.top3Share()).isCloseTo(0.80, offset(1e-9));
    }

    @Test
    void busFactor_exactFiftyPercentTieCountsTheCrosser() {

        // given the top contributor holds exactly half
        var counts = List.of(5L, 3L, 2L);

        // when
        var actual = Distribution.of(counts);

        // then cumulative share reaches >= 50% at the first contributor
        assertThat(actual.busFactor()).isEqualTo(1);
    }

    @Test
    void busFactor_allEqualSpread() {

        // given four members at 25% each
        var counts = List.of(5L, 5L, 5L, 5L);

        // when
        var actual = Distribution.of(counts);

        // then two of them together first reach 50%
        assertThat(actual.busFactor()).isEqualTo(2);
        assertThat(actual.top3Share()).isCloseTo(0.75, offset(1e-9));
    }

    @Test
    void shares_sortedDescendingAndAnonymous() {

        // when
        var actual = Distribution.of(List.of(1L, 6L, 3L));

        // then counts in, sorted shares out — identity never entered this type
        assertThat(actual.shares()).containsExactly(0.6, 0.3, 0.1);
    }
}
