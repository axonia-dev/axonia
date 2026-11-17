package dev.axonia.identity.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamePatternTest {

    private static final NamePattern HOUSE_FORMAT = NamePattern.parse("{family}, {given}-NORTHWIND");

    @Test
    void readsTheGivenAndFamilyNameOutOfAHouseFormat() {

        assertThat(HOUSE_FORMAT.read("Rochat, Aurélia-NORTHWIND")).contains(new ReadName("Aurélia Rochat", "Aurélia"));
    }

    @Test
    void aGivenNameMayCarryTheSameCharacterAsTheLiteral() {

        assertThat(HOUSE_FORMAT.read("Rochat, Jean-Pierre-NORTHWIND"))
                .contains(new ReadName("Jean-Pierre Rochat", "Jean-Pierre"));
    }

    @Test
    void extraWhitespaceAroundAndInsideIsTolerated() {

        assertThat(HOUSE_FORMAT.read("  Rochat,   Aurélia-NORTHWIND "))
                .contains(new ReadName("Aurélia Rochat", "Aurélia"));
    }

    @Test
    void literalsMatchIgnoringCaseAndThePartsKeepTheirOwn() {

        assertThat(HOUSE_FORMAT.read("ROCHAT, ÉLISE-northwind")).contains(new ReadName("ÉLISE ROCHAT", "ÉLISE"));
    }

    @Test
    void aNameThatDoesNotMatchTheWholePatternIsNotRead() {

        assertThat(HOUSE_FORMAT.read("Rochat, Aurélia")).isEmpty();
        assertThat(HOUSE_FORMAT.read("octocat")).isEmpty();
        assertThat(HOUSE_FORMAT.read(null)).isEmpty();
    }

    @Test
    void aPartMadeOnlyOfWhitespaceIsNoMatch() {

        assertThat(HOUSE_FORMAT.read("Rochat,  -NORTHWIND")).isEmpty();
        assertThat(NamePattern.parse("{given}-{family}").read("Aurélia- \u00A0")).isEmpty();
    }

    @Test
    void aNoBreakSpaceCountsAsWhitespace() {

        assertThat(HOUSE_FORMAT.read("\u00A0Rochat\u00A0,\u00A0Aurélia\u00A0-NORTHWIND\u00A0"))
                .contains(new ReadName("Aurélia Rochat", "Aurélia"));
    }

    @Test
    void theEarlierPlaceholderTakesTheShortestMatch() {

        assertThat(NamePattern.parse("{given} {family}").read("Mary Ann Smith"))
                .contains(new ReadName("Mary Ann Smith", "Mary"));
    }

    @Test
    void eachPartNeedsAtLeastOneCharacter() {

        assertThat(NamePattern.parse("{family}-{given}").read("Smith-")).isEmpty();
    }

    @Test
    void keepsThePatternTextTrimmed() {

        assertThat(NamePattern.parse("  {given} {family}-EXTERNAL ").text()).isEqualTo("{given} {family}-EXTERNAL");
    }

    @Test
    void twoPatternsOfTheSameTextAreEqual() {

        assertThat(NamePattern.parse("{given} {family}")).isEqualTo(NamePattern.parse("{given} {family}"))
                .hasSameHashCodeAs(NamePattern.parse("{given} {family}")).isNotEqualTo(HOUSE_FORMAT)
                .isNotEqualTo("{given} {family}").hasToString("{given} {family}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{given}", "{family}", "{given} {given} {family}", "{given} {family} {family}",
            "{given}{family}", "{given} {middle} {family}", "", "   "})
    void refusesAPatternWithoutExactlyOneOfEachPlaceholderApart(String text) {

        assertThatThrownBy(() -> NamePattern.parse(text)).isInstanceOfSatisfying(ValidationException.class,
                refusal -> assertThat(refusal.getCode()).isEqualTo("NAME_PATTERN_INVALID"));
    }

    @Test
    void refusesAMissingPattern() {

        assertThatThrownBy(() -> NamePattern.parse(null)).isInstanceOfSatisfying(ValidationException.class,
                refusal -> assertThat(refusal.getCode()).isEqualTo("NAME_PATTERN_INVALID"));
    }

    @Test
    void refusesAPatternLongerThanItsColumn() {

        var tooLong = "{given} {family}-" + "X".repeat(NamePattern.MAX_LENGTH);

        assertThatThrownBy(() -> NamePattern.parse(tooLong)).isInstanceOfSatisfying(ValidationException.class,
                refusal -> assertThat(refusal.getCode()).isEqualTo("NAME_PATTERN_INVALID"));
    }
}
