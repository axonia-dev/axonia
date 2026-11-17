package dev.axonia.roundup.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ThumbnailRequestTest {

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {"#de3b78, #de3b78", "#DE3B78, #de3b78", "#abc, #aabbcc", "#ABC, #aabbcc",
            "null, #786a7e", "red, #786a7e", "#12345, #786a7e", "'', #786a7e"})
    void theAccentIsSixLowercaseHexDigitsOrTheNeutralAccent(String accentColor, String expected) {

        assertThat(ThumbnailRequest.accentHexOf(accentColor)).isEqualTo(expected);
    }

    @Test
    void anAbsentFocusIsTheCentre() {

        assertThat(ThumbnailRequest.focusOrCentre(null)).isEqualTo(50);
        assertThat(ThumbnailRequest.focusOrCentre(20)).isEqualTo(20);
    }
}
