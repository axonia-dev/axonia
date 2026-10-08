package dev.axonia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class InitialsTest {

    @ParameterizedTest
    @CsvSource({"Ada Lovelace,AL", "Ada,A", "ada lovelace,AL", "'  Ada   Lovelace  ',AL",
            "Grace Brewster Murray Hopper,GB", "Acme Corp,AC"})
    void aNameBecomesUpToTwoUppercaseLetters(String name, String expected) {

        assertThat(Initials.of(name)).isEqualTo(expected);
    }

    @Test
    void aNameWithNothingInItBecomesNothingRatherThanThrowing() {

        assertThat(Initials.of("   ")).isEmpty();
    }

    /**
     * The whole reason this reads code points rather than {@code char}s. "𝒜da" opens with a supplementary-plane letter
     * that occupies two {@code char}s, so {@code charAt(0)} would put a lone, unpaired surrogate into a member's inbox,
     * and a {@code length()} cap would stop after that one letter and never reach the second word.
     */
    @Test
    void aLetterOutsideTheBasicMultilingualPlaneSurvivesWholeAndStillLeavesRoomForTheSecondWord() {

        var initials = Initials.of("𝒜da Lovelace");

        assertThat(initials.codePointCount(0, initials.length())).isEqualTo(2);
        assertThat(initials).isEqualTo("𝒜L");
    }
}
