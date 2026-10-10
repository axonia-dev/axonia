package dev.axonia.communication.application;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code HmacSHA256} itself is mandated always-available, so the two-arg {@link HmacSha256#sign(byte[], byte[])} used
 * in production can never actually hit its own catch block. The three-arg overload exists to let this test drive that
 * same catch with a name no JCA provider registers, without touching the JVM's registered security providers.
 */
class HmacSha256Test {

    @Test
    void signsWithTheGivenSecret() {

        var secret = "a-secret".getBytes(StandardCharsets.UTF_8);
        var payload = "a-payload".getBytes(StandardCharsets.UTF_8);

        assertThat(HmacSha256.sign(secret, payload)).isEqualTo(HmacSha256.sign("HmacSHA256", secret, payload));
    }

    @Test
    void anUnavailableAlgorithmFailsClosedRatherThanSilentlySigningWithSomethingElse() {

        var secret = "a-secret".getBytes(StandardCharsets.UTF_8);
        var payload = "a-payload".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> HmacSha256.sign("NoSuchHmacAlgorithm", secret, payload))
                .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(NoSuchAlgorithmException.class);
    }
}
