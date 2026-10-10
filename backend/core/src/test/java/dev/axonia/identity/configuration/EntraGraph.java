package dev.axonia.identity.configuration;

import java.time.Clock;

import org.springframework.boot.micrometer.observation.autoconfigure.ObservationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import dev.axonia.identity.adapter.out.entra.EntraPhotoSource;
import dev.axonia.identity.adapter.out.entra.GraphGroups;
import dev.axonia.shared.api.http.ClientCredentialsAuthorization;
import dev.axonia.shared.api.metrics.IntegrationMetrics;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Microsoft Graph as the application wires it, on a builder a test has bound to its own mock server. */
public final class EntraGraph {

    public static final String GRAPH_BASE = "https://graph.microsoft.com";

    public static final EntraGraphProperties CONFIGURED = new EntraGraphProperties("app-client-id", "app-secret");

    private EntraGraph() {
    }

    public static RestClient client(RestClient.Builder bound, IntegrationMetrics metrics, Clock clock) {
        return client(bound, metrics, authorization(clock), new ObservationProperties());
    }

    public static RestClient client(RestClient.Builder bound, IntegrationMetrics metrics,
            ClientCredentialsAuthorization authorization, ObservationProperties observations) {
        return new EntraIdpConfig().entraGraphRestClient(bound, metrics, authorization, observations);
    }

    public static ClientCredentialsAuthorization authorization(Clock clock) {
        return new EntraIdpConfig().entraGraphAuthorization(CONFIGURED, clock);
    }

    public static EntraPhotoSource photoSource(RestClient.Builder bound, IntegrationMetrics metrics, Clock clock) {
        return new EntraPhotoSource(client(bound, metrics, clock));
    }

    public static GraphGroups graphGroups(RestClient.Builder bound, IntegrationMetrics metrics, Clock clock) {
        return new GraphGroups(client(bound, metrics, clock));
    }

    public static String tokenEndpoint(String tenant) {
        return "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token";
    }

    public static String tokenJson(String token) {
        return """
                {"access_token": "%s", "token_type": "Bearer", "expires_in": 3600}
                """.formatted(token);
    }

    public static void expectToken(MockRestServiceServer server, String tenant, String token) {
        server.expect(requestTo(tokenEndpoint(tenant))).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(tokenJson(token), MediaType.APPLICATION_JSON));
    }
}
