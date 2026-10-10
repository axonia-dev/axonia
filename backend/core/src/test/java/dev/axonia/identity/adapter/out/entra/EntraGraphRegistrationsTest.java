package dev.axonia.identity.adapter.out.entra;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import dev.axonia.identity.configuration.EntraGraph;
import dev.axonia.identity.configuration.EntraGraphProperties;
import dev.axonia.shared.api.http.ClientCredentialsAuthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EntraGraphRegistrationsTest {

    private static final String TENANT = "11111111-2222-3333-4444-555555555555";

    private final EntraGraphRegistrations registrations = new EntraGraphRegistrations(EntraGraph.CONFIGURED);

    @Test
    void aTenantsRegistrationIsAClientCredentialsGrantForGraphAtThatTenantsTokenEndpoint() {

        // when
        var registration = registrations.findByRegistrationId("entra:tenant-acme");

        // then
        assertThat(registration.getClientId()).isEqualTo("app-client-id");
        assertThat(registration.getClientSecret()).isEqualTo("app-secret");
        assertThat(registration.getClientAuthenticationMethod())
                .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        assertThat(registration.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(registration.getScopes()).containsExactly("https://graph.microsoft.com/.default");
        assertThat(registration.getProviderDetails().getTokenUri())
                .isEqualTo("https://login.microsoftonline.com/tenant-acme/oauth2/v2.0/token");
    }

    @Test
    void theTokenServerReceivesTheTenantsTokenPathAsWritten() {

        // given
        var builder = RestClient.builder().baseUrl(EntraGraph.GRAPH_BASE);
        var server = MockRestServiceServer.bindTo(builder).build();
        EntraGraph.expectToken(server, TENANT, "graph-token");
        server.expect(requestTo(EntraGraph.GRAPH_BASE + "/v1.0/groups")).andRespond(withSuccess());
        var graph = new ClientCredentialsAuthorization(registrations,
                new RequestAttributeClientRegistrationIdResolver(), Clock.systemUTC(), Duration.ofMinutes(5))
                .authorize(builder, UnaryOperator.identity());

        // when
        graph.get().uri("/v1.0/groups").attributes(EntraGraphRegistrations.actingFor(TENANT)).retrieve()
                .toBodilessEntity();

        // then - requestTo compares the URI as sent, so an encoding applied twice would fail here
        server.verify();
    }

    @Test
    void thereIsNoRegistrationWhileTheGraphAppIsNotConfigured() {

        var unconfigured = new EntraGraphRegistrations(new EntraGraphProperties(null, null));

        assertThat(unconfigured.findByRegistrationId("entra:tenant-acme")).isNull();
    }

    @Test
    void anIdThatNamesNoEntraTenantHasNoRegistration() {

        assertThat(registrations.findByRegistrationId("directory-client")).isNull();
    }

    @Test
    void aCallActsForTheRegistrationOfItsOwnTenant() {

        // given
        var attributes = new HashMap<String, Object>();

        // when
        EntraGraphRegistrations.actingFor("tenant-acme").accept(attributes);

        // then
        assertThat(attributes).containsValue("entra:tenant-acme");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aCallWithoutATenantIsRefusedNamingTheTenant(String tenant) {

        assertThatThrownBy(() -> EntraGraphRegistrations.actingFor(tenant)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aadTenantId");
    }
}
