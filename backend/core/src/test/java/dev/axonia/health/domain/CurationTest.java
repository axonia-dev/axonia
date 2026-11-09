package dev.axonia.health.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class CurationTest {

    @Test
    void of() {

        // when
        var actual = Curation.of(List.of(9L, 1L));

        // then
        assertThat(actual.acts()).isEqualTo(10);
        assertThat(actual.topShare()).isCloseTo(0.9, offset(1e-9));
    }

    @Test
    void of_noActs() {

        // when
        var actual = Curation.of(List.of());

        // then
        assertThat(actual).isEqualTo(new Curation(0, 0.0));
    }
}
