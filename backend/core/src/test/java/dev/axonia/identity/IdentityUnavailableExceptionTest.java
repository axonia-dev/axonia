package dev.axonia.identity;

import java.io.IOException;
import java.net.http.HttpTimeoutException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityUnavailableExceptionTest {

    /** A support screen says more about a request that started but did not finish in time, so the flag must hold. */
    @Test
    void timeout() {

        // given
        var transportFailure = new HttpTimeoutException("request timed out");

        // when
        var actual = IdentityUnavailableException.timeout(transportFailure);

        // then
        assertThat(actual.isTimeout()).isTrue();
        assertThat(actual).hasMessage("request timed out").hasCause(transportFailure);
    }

    @Test
    void flatFailure() {

        // given
        var transportFailure = new IOException("connection refused");

        // when
        var actual = IdentityUnavailableException.flatFailure(transportFailure);

        // then
        assertThat(actual.isTimeout()).isFalse();
        assertThat(actual).hasMessage("connection refused").hasCause(transportFailure);
    }
}
