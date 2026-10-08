package dev.axonia.health;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrendTest {

    @Test
    void of_up() {

        // when
        var actual = Trend.of(5, 3);

        // then
        assertThat(actual).usingRecursiveAssertion().isEqualTo(new Trend(3, Trend.Direction.UP));
    }

    @Test
    void of_down() {

        // when
        var actual = Trend.of(0.28, 0.40);

        // then
        assertThat(actual).usingRecursiveAssertion().isEqualTo(new Trend(0.40, Trend.Direction.DOWN));
    }

    @Test
    void of_flat() {

        // when
        var actual = Trend.of(4, 4);

        // then
        assertThat(actual).usingRecursiveAssertion().isEqualTo(new Trend(4, Trend.Direction.FLAT));
    }
}
