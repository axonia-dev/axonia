package dev.axonia.shared.api.colour;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonTintsTest {

    @Test
    void theSameNameAlwaysWearsTheSameTint() {

        assertThat(PersonTints.of("Ada Lovelace")).isEqualTo(PersonTints.of("Ada Lovelace"));
    }

    @Test
    void namesSpreadOverThePalettesThreeTints() {

        var tints = Stream.of("Ada", "Grace", "Linus", "Margaret", "Dennis", "Barbara", "Ken", "Edsger")
                .map(PersonTints::of).collect(Collectors.toSet());

        assertThat(tints).isSubsetOf(Set.of("#de3b78", "#6e3ceb", "#1f9d63")).hasSizeGreaterThan(1);
    }

    @Test
    void aNegativeHashCodeStillPicksATint() {

        assertThat("polygenelubricants".hashCode()).isNegative();
        assertThat(PersonTints.of("polygenelubricants")).isIn("#de3b78", "#6e3ceb", "#1f9d63");
    }
}
