package dev.axonia.installation.domain;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmTest {

    private static final byte[] KEY = new byte[32];

    private static final byte[] NONCE = new byte[12];

    private static final byte[] AAD = "mail/password".getBytes(StandardCharsets.UTF_8);

    private static final byte[] TEXT = "s3cret".getBytes(StandardCharsets.UTF_8);

    @Test
    void opensWhatItSealed() {

        // given
        var sealed = AesGcm.STANDARD.encrypt(KEY, NONCE, AAD, TEXT);

        // when
        var actual = AesGcm.STANDARD.decrypt(KEY, NONCE, AAD, sealed);

        // then
        assertThat(sealed).hasSize(TEXT.length + 16);
        assertThat(actual).hasValueSatisfying(text -> assertThat(text).isEqualTo(TEXT));
    }

    @Test
    void opensNothingSealedForAnotherField() {

        // given
        var sealed = AesGcm.STANDARD.encrypt(KEY, NONCE, AAD, TEXT);

        // when
        var actual = AesGcm.STANDARD.decrypt(KEY, NONCE, "mail/username".getBytes(StandardCharsets.UTF_8), sealed);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void opensNothingThatWasAltered() {

        // given
        var sealed = AesGcm.STANDARD.encrypt(KEY, NONCE, AAD, TEXT);
        sealed[0] ^= 1;

        // when
        var actual = AesGcm.STANDARD.decrypt(KEY, NONCE, AAD, sealed);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void refusesATransformationTheJvmLacks() {

        // given
        var unavailable = new AesGcm("AES/NOPE/NoPadding");

        // then
        assertThatThrownBy(() -> unavailable.encrypt(KEY, NONCE, AAD, TEXT)).isInstanceOf(IllegalStateException.class)
                .hasMessage("AES/NOPE/NoPadding is unavailable on this JVM");
        assertThatThrownBy(() -> unavailable.decrypt(KEY, NONCE, AAD, TEXT)).isInstanceOf(IllegalStateException.class)
                .hasMessage("AES/NOPE/NoPadding is unavailable on this JVM");
    }
}
