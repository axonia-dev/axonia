package dev.axonia.identity.domain;

import java.util.Collections;
import java.util.List;

import dev.axonia.shared.api.error.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamePatternsTest {

    private static final String RECEIVED = "Rochat, Aurélia-NORTHWIND";

    @Test
    void theFirstMatchingPatternWins() {

        var patterns = NamePatterns.of(List.of("{family}, {given}-EXTERNAL", "{family}, {given}-NORTHWIND"));

        assertThat(patterns.read(RECEIVED)).isEqualTo(new ReadName("Aurélia Rochat", "Aurélia"));
        assertThat(patterns.matchedIndex(RECEIVED)).isEqualTo(1);
        assertThat(patterns.texts()).containsExactly("{family}, {given}-EXTERNAL", "{family}, {given}-NORTHWIND");
    }

    @Test
    void withNoPatternTheNameIsKeptWhole() {

        assertThat(NamePatterns.NONE.read(RECEIVED)).isEqualTo(new ReadName(RECEIVED, null));
        assertThat(NamePatterns.NONE.matchedIndex(RECEIVED)).isEqualTo(-1);
        assertThat(NamePatterns.NONE.patterns()).isEmpty();
    }

    @Test
    void refusesMoreThanTenPatterns() {

        var eleven = Collections.nCopies(NamePatterns.MAX + 1, "{given} {family}");

        assertThatThrownBy(() -> NamePatterns.of(eleven)).isInstanceOfSatisfying(ValidationException.class,
                refusal -> assertThat(refusal.getCode()).isEqualTo("NAME_PATTERNS_TOO_MANY"));
    }

    @Test
    void acceptsExactlyTenPatterns() {

        assertThat(NamePatterns.of(Collections.nCopies(NamePatterns.MAX, "{given} {family}")).patterns())
                .hasSize(NamePatterns.MAX);
    }

    @Test
    void aMissingNameStaysMissing() {

        var patterns = NamePatterns.of(List.of("{family}, {given}-NORTHWIND"));

        assertThat(patterns.read(null)).isEqualTo(new ReadName(null, null));
        assertThat(patterns.matchedIndex(null)).isEqualTo(-1);
    }
}
