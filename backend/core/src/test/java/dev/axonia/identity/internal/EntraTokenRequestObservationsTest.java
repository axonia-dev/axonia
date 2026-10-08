package dev.axonia.identity.internal;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationProperties;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import dev.axonia.IntegrationMetrics;
import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The Graph client's observations, captured as their low-cardinality key values: those are what become metric tags and
 * span attributes, so those are what must never name a customer's tenant.
 */
class EntraTokenRequestObservationsTest {

    private static final String TENANT = "11111111-2222-3333-4444-555555555555";

    private final List<Observed> observed = new CopyOnWriteArrayList<>();
    private final RestClient.Builder builder = RestClient.builder().observationRegistry(capturingInto(observed));
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final IntegrationMetrics metrics = new IntegrationMetrics(new SimpleMeterRegistry());
    private final RestClient graph = EntraGraph.client(builder, metrics, Clock.systemUTC());

    @Test
    void theTokenRequestIsObservedUnderTheTenantTemplateAndNeverTheTenantId() {

        // when
        readAPhotoActingFor(graph, TENANT);

        // then
        var tokenRequest = observedOn("login.microsoftonline.com").keyValues();
        assertThat(tokenRequest).containsEntry("uri", "/{aadTenantId}/oauth2/v2.0/token");
        assertThat(tokenRequest.values()).noneMatch(value -> value.contains(TENANT));
    }

    @Test
    void theTokenRequestFollowsARenamedClientRequestMeter() {

        // given - management.observations.http.client.requests.name, which every other client request follows
        var observations = new ObservationProperties();
        observations.getHttp().getClient().getRequests().setName("axonia.outbound.requests");
        var renamed = EntraGraph.client(builder, metrics, EntraGraph.authorization(Clock.systemUTC()), observations);

        // when
        readAPhotoActingFor(renamed, TENANT);

        // then
        assertThat(observedOn("login.microsoftonline.com").name()).isEqualTo("axonia.outbound.requests");
    }

    @Test
    void aGraphRequestOnTheSameClientKeepsItsOwnTemplate() {

        // when
        readAPhotoActingFor(graph, TENANT);

        // then
        assertThat(observedOn("graph.microsoft.com").keyValues()).containsEntry("uri", "/v1.0/users/{id}/photo");
    }

    private void readAPhotoActingFor(RestClient graph, String tenant) {
        EntraGraph.expectToken(server, tenant, "graph-token");
        server.expect(requestTo(EntraGraph.GRAPH_BASE + "/v1.0/users/entra-1/photo")).andRespond(withSuccess());
        graph.get().uri("/v1.0/users/{id}/photo", "entra-1").attributes(EntraGraphRegistrations.actingFor(tenant))
                .retrieve().toBodilessEntity();
        server.verify();
    }

    private Observed observedOn(String host) {
        return observed.stream().filter(observation -> host.equals(observation.keyValues().get("client.name")))
                .findFirst().orElseThrow();
    }

    private record Observed(String name, Map<String, String> keyValues) {
    }

    private static ObservationRegistry capturingInto(List<Observed> observed) {
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<ClientRequestObservationContext>() {

            @Override
            public boolean supportsContext(Observation.Context context) {
                return context instanceof ClientRequestObservationContext;
            }

            @Override
            public void onStop(ClientRequestObservationContext context) {
                observed.add(new Observed(context.getName(), context.getLowCardinalityKeyValues().stream()
                        .collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue))));
            }
        });
        return registry;
    }
}
