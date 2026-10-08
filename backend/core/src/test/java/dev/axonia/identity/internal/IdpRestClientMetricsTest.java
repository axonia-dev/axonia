package dev.axonia.identity.internal;

import java.io.IOException;
import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import dev.axonia.OutboundCallProbe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The two Entra clients count what they send. Asserted on the beans the configuration class actually builds, because
 * the counting lives on an interceptor attached there: a test that built its own {@code RestClient} would pass against
 * a bean that had lost it.
 *
 * <p>
 * Both are tagged {@code entra} on purpose - the login endpoint and Graph are one vendor, and splitting them would
 * answer a question no alert asks.
 */
class IdpRestClientMetricsTest {

    @Test
    void theEntraGraphClientCountsBothOutcomes() throws IOException {

        try (var probe = new OutboundCallProbe()) {
            var client = EntraGraph.client(RestClient.builder(), probe.metrics(), Clock.systemUTC());

            exercise(client, probe);

            assertThat(probe.calls("entra", "success")).isEqualTo(1.0);
            assertThat(probe.calls("entra", "failure")).isEqualTo(1.0);
        }
    }

    @Test
    void theEntraLoginClientCountsUnderTheSameNameAsGraph() throws IOException {

        try (var probe = new OutboundCallProbe()) {
            var client = new EntraIdpConfig().entraLoginRestClient(RestClient.builder(), probe.metrics());

            exercise(client, probe);

            assertThat(probe.calls("entra", "success")).isEqualTo(1.0);
            assertThat(probe.calls("entra", "failure")).isEqualTo(1.0);
        }
    }

    private static void exercise(RestClient client, OutboundCallProbe probe) {

        client.get().uri(probe.ok()).retrieve().toBodilessEntity();
        assertThatThrownBy(() -> client.get().uri(probe.refused()).retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }
}
