package dev.axonia;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidTimeZoneTest {

    private final ValidTimeZone.Validator validator = new ValidTimeZone.Validator();

    @Test
    void nullIsValid() {

        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void blankIsValid() {

        assertThat(validator.isValid("   ", null)).isTrue();
    }

    @Test
    void aKnownIanaZoneIsValid() {

        assertThat(validator.isValid("Europe/Zurich", null)).isTrue();
    }

    @Test
    void anUnresolvableZoneIsInvalid() {

        assertThat(validator.isValid("Not/AZone", null)).isFalse();
    }
}
