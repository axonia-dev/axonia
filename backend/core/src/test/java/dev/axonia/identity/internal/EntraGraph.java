package dev.axonia.identity.internal;

import java.time.Clock;

import org.springframework.boot.micrometer.observation.autoconfigure.ObservationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import dev.axonia.ClientCredentialsAuthorization;
import dev.axonia.IntegrationMetrics;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Microsoft Graph as the application wires it, on a builder a test has bound to its own mock server. */
final class EntraGraph {

    static final String GRAPH_BASE = "https://graph.microsoft.com";

    static final EntraGraphProperties CONFIGURED = new EntraGraphProperties("app-client-id", "app-secret");

    private EntraGraph() {
    }

    static RestClient client(RestClient.Builder bound, IntegrationMetrics metrics, Clock clock) {
        return client(bound, metrics, authorization(clock), new ObservationProperties());
    }

    static RestClient client(RestClient.Builder bound, IntegrationMetrics metrics,
            ClientCredentialsAuthorization authorization, ObservationProperties observations) {
        return new EntraIdpConfig().entraGraphRestClient(bound, metrics, authorization, observations);
    }

    static ClientCredentialsAuthorization authorization(Clock clock) {
        return new EntraIdpConfig().entraGraphAuthorization(CONFIGURED, clock);
    }

    static EntraPhotoSource photoSource(RestClient.Builder bound, IntegrationMetrics metrics, Clock clock) {
        return new EntraPhotoSource(client(bound, metrics, clock));
    }

    static GraphGroups graphGroups(RestClient.Builder bound, IntegrationMetrics metrics, Clock clock) {
        return new GraphGroups(client(bound, metrics, clock));
    }

    static String tokenEndpoint(String tenant) {
        return "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token";
    }

    static String tokenJson(String token) {
        return """
                {"access_token": "%s", "token_type": "Bearer", "expires_in": 3600}
                """.formatted(token);
    }

    static void expectToken(MockRestServiceServer server, String tenant, String token) {
        server.expect(requestTo(tokenEndpoint(tenant))).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(tokenJson(token), MediaType.APPLICATION_JSON));
    }
}
