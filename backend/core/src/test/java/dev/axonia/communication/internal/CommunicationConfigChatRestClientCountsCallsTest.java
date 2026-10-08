package dev.axonia.communication.internal;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import dev.axonia.OutboundCallProbe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The chat client counts what it posts, under the fixed tag {@code chat} rather than under the host it posted to. That
 * distinction is the whole reason the integration is named at the bean definition: the webhook URLs this client posts
 * to are configured per tenant, so a tag derived from the hostname would grow a new metric series per customer.
 */
class CommunicationConfigChatRestClientCountsCallsTest {

    @Test
    void theChatClientCountsBothOutcomes() throws IOException {

        try (var probe = new OutboundCallProbe()) {
            var client = new CommunicationConfig().chatRestClient(RestClient.builder(), HttpClientSettings.defaults(),
                    ClientHttpRequestFactoryBuilder.detect(), probe.metrics());

            client.post().uri(probe.ok()).retrieve().toBodilessEntity();
            assertThatThrownBy(() -> client.post().uri(probe.refused()).retrieve().toBodilessEntity())
                    .isInstanceOf(RestClientResponseException.class);

            assertThat(probe.calls("chat", "success")).isEqualTo(1.0);
            assertThat(probe.calls("chat", "failure")).isEqualTo(1.0);
        }
    }
}
