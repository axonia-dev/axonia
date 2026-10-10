package dev.axonia.installation.domain;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacTest {

    /** RFC 4231, test case 2. */
    @Test
    void signsTheRfcVector() {

        // when
        var actual = Hmac.sign("Jefe".getBytes(StandardCharsets.US_ASCII),
                "what do ya want for nothing?".getBytes(StandardCharsets.US_ASCII));

        // then
        assertThat(HexFormat.of().formatHex(actual))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    void refusesAnAlgorithmTheJvmLacks() {

        assertThatThrownBy(() -> Hmac.sign("HmacNOPE", new byte[32], new byte[1]))
                .isInstanceOf(IllegalStateException.class).hasMessage("HmacNOPE is unavailable on this JVM");
    }
}
