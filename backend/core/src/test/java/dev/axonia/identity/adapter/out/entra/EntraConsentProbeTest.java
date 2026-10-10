package dev.axonia.identity.adapter.out.entra;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationProperties;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import dev.axonia.identity.adapter.out.entra.EntraGraphTokenSource.ProbeResult;
import dev.axonia.identity.api.GraphConsent;
import dev.axonia.identity.configuration.EntraGraph;
import dev.axonia.identity.configuration.EntraGraphProperties;
import dev.axonia.shared.api.http.ClientCredentialsAuthorization;
import dev.axonia.shared.api.metrics.IntegrationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@MockitoSettings
class EntraConsentProbeTest {

    private static final String TENANT = "test-aad-tenant";

    @Mock
    private EntraGraphTokenSource tokenSource;

    @Mock
    private EntraGraphProperties graphProperties;

    @Spy
    private JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private ClientCredentialsAuthorization graphTokens;

    @InjectMocks
    private EntraConsentProbe sut;

    @Test
    void consentUrlFor_withTheGraphAppConfigured_composesTheAdminConsentUrl() {

        // given
        when(graphProperties.isConfigured()).thenReturn(true);
        when(graphProperties.clientId()).thenReturn("app-client-id");

        // when / then
        assertThat(sut.consentUrlFor(TENANT))
                .isEqualTo("https://login.microsoftonline.com/" + TENANT + "/adminconsent?client_id=app-client-id");
    }

    @Test
    void consentUrlFor_withTheGraphAppNotConfigured_isNull() {

        // given - graphProperties.isConfigured() is false by default (unstubbed mock)

        // when / then
        assertThat(sut.consentUrlFor(TENANT)).isNull();
    }

    @Test
    void check_withBothRequiredPermissions_isGranted() {

        // given
        when(tokenSource.probe(TENANT)).thenReturn(new ProbeResult(GraphConsent.GRANTED,
                tokenWithRoles("GroupMember.Read.All", "ProfilePhoto.Read.All"), null));

        // when / then
        assertThat(sut.check(TENANT).status()).isEqualTo(GraphConsent.GRANTED);
    }

    /**
     * An administrator who re-consents to add a permission is told GRANTED by this very check, so the Graph token kept
     * from before the consent must not outlive it: Graph would refuse it with a 403, which keeps a token, for the rest
     * of its hour.
     */
    @Test
    void check_whenGranted_dropsTheTenantsGraphTokenSoTheNextGraphCallMintsAFreshOne() {

        // given - a Graph token minted before the administrator re-consented
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var authorization = EntraGraph.authorization(Clock.systemUTC());
        var graph = EntraGraph.client(builder, new IntegrationMetrics(new SimpleMeterRegistry()), authorization,
                new ObservationProperties());
        var probe = new EntraConsentProbe(tokenSource, graphProperties, jsonMapper, authorization);
        when(tokenSource.probe(TENANT)).thenReturn(new ProbeResult(GraphConsent.GRANTED,
                tokenWithRoles("GroupMember.Read.All", "ProfilePhoto.Read.All"), null));
        EntraGraph.expectToken(server, TENANT, "before-consent");
        expectGroupsReadCarrying(server, "before-consent");
        EntraGraph.expectToken(server, TENANT, "after-consent");
        expectGroupsReadCarrying(server, "after-consent");
        readGroups(graph);

        // when
        probe.check(TENANT);

        // then
        readGroups(graph);
        server.verify();
    }

    @Test
    void check_whenGranted_forgetsOnlyThatTenantsToken() {

        // given
        when(tokenSource.probe(TENANT)).thenReturn(new ProbeResult(GraphConsent.GRANTED,
                tokenWithRoles("GroupMember.Read.All", "ProfilePhoto.Read.All"), null));

        // when
        sut.check(TENANT);

        // then
        verify(graphTokens).forget("entra:" + TENANT);
        verifyNoMoreInteractions(graphTokens);
    }

    @Test
    void check_whenAPermissionIsMissing_isIncompleteAndNamesIt() {

        // given - a customer who consented before ProfilePhoto.Read.All was added to the app
        when(tokenSource.probe(TENANT))
                .thenReturn(new ProbeResult(GraphConsent.GRANTED, tokenWithRoles("GroupMember.Read.All"), null));

        // when
        var verdict = sut.check(TENANT);

        // then
        assertThat(verdict.status()).isEqualTo(GraphConsent.INCOMPLETE);
        assertThat(verdict.detail()).contains("ProfilePhoto.Read.All");
        verifyNoInteractions(graphTokens);
    }

    @Test
    void check_whenTheTokenCarriesNoRolesAtAll_isIncomplete() {

        // given - Entra issues app-only tokens with no roles claim when nothing was granted
        when(tokenSource.probe(TENANT)).thenReturn(new ProbeResult(GraphConsent.GRANTED, tokenWithRoles(), null));

        // when / then
        assertThat(sut.check(TENANT).status()).isEqualTo(GraphConsent.INCOMPLETE);
    }

    @Test
    void check_whenTheRolesClaimIsMissingEntirely_isIncomplete() {

        // given - a payload that never carries a roles key at all, not merely an empty one
        when(tokenSource.probe(TENANT))
                .thenReturn(new ProbeResult(GraphConsent.GRANTED, tokenWithPayload("{\"aud\":\"graph\"}"), null));

        // when / then
        assertThat(sut.check(TENANT).status()).isEqualTo(GraphConsent.INCOMPLETE);
    }

    @Test
    void check_whenTheRolesClaimIsNotAnArray_isIncomplete() {

        // given - a malformed roles claim must degrade the same way an absent one does, not throw
        when(tokenSource.probe(TENANT)).thenReturn(
                new ProbeResult(GraphConsent.GRANTED, tokenWithPayload("{\"roles\":\"GroupMember.Read.All\"}"), null));

        // when / then
        assertThat(sut.check(TENANT).status()).isEqualTo(GraphConsent.INCOMPLETE);
    }

    @Test
    void check_whenTheTokenCannotBeDecoded_staysGrantedRatherThanInventingAProblem() {

        // given - Microsoft documents Graph tokens as opaque; a format change must not fabricate a failure
        when(tokenSource.probe(TENANT)).thenReturn(new ProbeResult(GraphConsent.GRANTED, "not-a-jwt", null));

        // when
        var verdict = sut.check(TENANT);

        // then - the mint succeeded, so the service principal provably exists
        assertThat(verdict.status()).isEqualTo(GraphConsent.GRANTED);
        assertThat(verdict.detail()).contains("permissions could not be read");
        verify(graphTokens).forget("entra:" + TENANT);
    }

    @Test
    void check_whenTheTokenPayloadIsNotValidJson_staysGrantedRatherThanInventingAProblem() {

        // given - a well-formed three-segment token whose payload fails to parse, not merely fails to split
        when(tokenSource.probe(TENANT))
                .thenReturn(new ProbeResult(GraphConsent.GRANTED, tokenWithGarbagePayload(), null));

        // when
        var verdict = sut.check(TENANT);

        // then
        assertThat(verdict.status()).isEqualTo(GraphConsent.GRANTED);
        assertThat(verdict.detail()).contains("permissions could not be read");
    }

    @Test
    void check_passesThroughANonGrantedVerdictUntouched() {

        // given
        when(tokenSource.probe(TENANT)).thenReturn(new ProbeResult(GraphConsent.ABSENT, null, "AADSTS700016: ..."));

        // when
        var verdict = sut.check(TENANT);

        // then
        assertThat(verdict.status()).isEqualTo(GraphConsent.ABSENT);
        assertThat(verdict.detail()).contains("AADSTS700016");
        verifyNoInteractions(graphTokens);
    }

    private static void expectGroupsReadCarrying(MockRestServiceServer server, String token) {
        server.expect(requestTo(EntraGraph.GRAPH_BASE + "/v1.0/groups"))
                .andExpect(header("Authorization", "Bearer " + token)).andRespond(withSuccess());
    }

    private static void readGroups(RestClient graph) {
        graph.get().uri("/v1.0/groups").attributes(EntraGraphRegistrations.actingFor(TENANT)).retrieve()
                .toBodilessEntity();
    }

    private static String tokenWithRoles(String... roles) {
        var payload = "{\"roles\":[" + Arrays.stream(roles).map(r -> "\"" + r + "\"").collect(Collectors.joining(","))
                + "]}";
        return tokenWithPayload(payload);
    }

    private static String tokenWithPayload(String payload) {
        return "header."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + ".signature";
    }

    private static String tokenWithGarbagePayload() {
        return "header." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString("not-json-at-all".getBytes(StandardCharsets.UTF_8)) + ".signature";
    }
}
