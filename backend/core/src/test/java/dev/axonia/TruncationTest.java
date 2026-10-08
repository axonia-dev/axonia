package dev.axonia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TruncationTest {

    @Test
    @DisplayName("a value longer than the bound is cut to it")
    void aValueLongerThanTheBoundIsCutToIt() {

        assertThat(Truncation.truncate("x".repeat(300), 255)).hasSize(255);
    }

    @Test
    @DisplayName("a value within the bound is returned unchanged")
    void aValueWithinTheBoundIsReturnedUnchanged() {

        assertThat(Truncation.truncate("short", 255)).isEqualTo("short");
    }

    @Test
    @DisplayName("null in, null out, since the caller's free text is often optional")
    void nullIsSafe() {

        assertThat(Truncation.truncate(null, 255)).isNull();
    }
}
