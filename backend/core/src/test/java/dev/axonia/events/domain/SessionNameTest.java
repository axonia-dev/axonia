package dev.axonia.events.domain;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionNameTest {

    @Test
    @DisplayName("the session's own title wins over its first titled slot")
    void ownTitleFirst() {

        assertThat(SessionName.of("S26E10 - DevTool", List.of("P1s", "Kafka"), "Backend Guild"))
                .isEqualTo("S26E10 - DevTool");
    }

    @Test
    @DisplayName("an untitled session is named after its first titled slot")
    void firstTitledSlotNext() {

        assertThat(SessionName.of(null, Arrays.asList(null, " ", "Kafka 101", "Flink"), "Backend Guild"))
                .isEqualTo("Kafka 101");
    }

    @Test
    @DisplayName("with no titled slot, a series date takes its series' title")
    void seriesTitleNext() {

        assertThat(SessionName.of("  ", Arrays.asList(null, ""), "Backend Guild")).isEqualTo("Backend Guild");
    }

    @Test
    @DisplayName("a session named by nothing at all is a Session")
    void genericLast() {

        assertThat(SessionName.of(null, List.of(), null)).isEqualTo("Session");
    }

    @Test
    @DisplayName("the name a session gives itself stops before its series")
    void itsOwnNameLeavesTheSeriesOut() {

        assertThat(SessionName.ofItsOwn(null, List.of("P1s"))).isEqualTo("P1s");
        assertThat(SessionName.ofItsOwn(" ", Arrays.asList(null, ""))).isNull();
    }

    @Test
    @DisplayName("a title is stored trimmed")
    void ownTitleIsTrimmed() {

        assertThat(SessionName.ownTitle("  S26E10 - DevTool \n")).isEqualTo("S26E10 - DevTool");
    }

    @Test
    @DisplayName("a blank or missing title clears the session's own title")
    void blankClears() {

        assertThat(SessionName.ownTitle(null)).isNull();
        assertThat(SessionName.ownTitle(" \t ")).isNull();
    }

    @Test
    @DisplayName("a title as long as the column holds is kept, one past it is refused")
    void lengthMatchesTheColumn() {

        var longest = "x".repeat(SessionName.MAX_LENGTH);

        assertThat(SessionName.ownTitle(" " + longest + " ")).isEqualTo(longest);
        assertThatThrownBy(() -> SessionName.ownTitle(longest + "x")).isInstanceOf(ValidationException.class)
                .hasMessageContaining(String.valueOf(SessionName.MAX_LENGTH));
    }
}
