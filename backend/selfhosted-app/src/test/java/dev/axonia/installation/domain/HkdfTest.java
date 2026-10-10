package dev.axonia.installation.domain;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HkdfTest {

    /** RFC 5869, test case 3: zero-length salt and info. */
    @Test
    void derivesTheRfcVector() {

        // when
        var actual = Hkdf.derive(HexFormat.of().parseHex("0b".repeat(22)), "", 42);

        // then
        assertThat(HexFormat.of().formatHex(actual))
                .isEqualTo("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8");
    }

    @Test
    void derivesADifferentKeyForEachPurpose() {

        // given
        var material = new byte[32];

        // when
        var seal = Hkdf.derive(material, "axonia/settings/seal", 32);
        var token = Hkdf.derive(material, "axonia/settings/test-token", 32);

        // then
        assertThat(seal).hasSize(32).isNotEqualTo(token);
    }

    @Test
    void refusesAnAlgorithmTheJvmLacks() {

        assertThatThrownBy(() -> Hkdf.derive("HKDF-NOPE", new byte[32], "purpose", 32))
                .isInstanceOf(IllegalStateException.class).hasMessage("HKDF-NOPE is unavailable on this JVM");
    }
}
