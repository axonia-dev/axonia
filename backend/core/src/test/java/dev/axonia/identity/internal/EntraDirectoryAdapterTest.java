package dev.axonia.identity.internal;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import dev.axonia.IntegrationMetrics;
import dev.axonia.SteppableClock;
import dev.axonia.identity.IdentityProviderDirectory.IdpGroup;
import dev.axonia.identity.IdpProvider;
import dev.axonia.identity.OrganizationIdp;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link EntraDirectoryAdapter}.
 *
 * <p>
 * Uses a single {@link MockRestServiceServer} for the Microsoft Graph client as the application wires it, so it answers
 * the client-credentials token request as well as Graph, in the order a real call reaches them.
 */
class EntraDirectoryAdapterTest {

    private static final String GRAPH_BASE = "https://graph.microsoft.com";
    private static final String GROUPS_PATH = "/v1.0/groups?$select=id,displayName";
    private static final String GROUPS_ENDPOINT = GRAPH_BASE + GROUPS_PATH;
    private static final String ACCESS_TOKEN = "entra-test-token";
    private static final String DEFAULT_TENANT = "tenant-default";
    private static final OrganizationIdp DEFAULT_IDP = new OrganizationIdp(IdpProvider.ENTRA, DEFAULT_TENANT, true);

    private final IntegrationMetrics metrics = new IntegrationMetrics(new SimpleMeterRegistry());
    private final SteppableClock clock = new SteppableClock(Instant.parse("2020-01-01T00:00:00Z"));

    private MockRestServiceServer server;
    private EntraDirectoryAdapter adapter;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new EntraDirectoryAdapter(EntraGraph.graphGroups(builder, metrics, clock));
    }

    // -------------------------------------------------------------------------
    // Mapping: IdpGroup fields
    // -------------------------------------------------------------------------

    @Test
    void listGroups_mapsIdAndDisplayNameFromGraphResponse() {

        // given
        EntraGraph.expectToken(server, DEFAULT_TENANT, ACCESS_TOKEN);
        expectGroupsEndpoint(singlePageJson("""
                {"id": "g1-uuid", "displayName": "Engineering Leads"},
                {"id": "g2-uuid", "displayName": "Platform Team"}
                """));

        // when
        var result = adapter.listGroups("default", DEFAULT_IDP);

        // then
        server.verify();
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new IdpGroup("g1-uuid", "Engineering Leads"));
        assertThat(result.get(1)).isEqualTo(new IdpGroup("g2-uuid", "Platform Team"));
    }

    @Test
    void listGroups_emptyGraphResponse_returnsEmptyList() {

        // given
        EntraGraph.expectToken(server, DEFAULT_TENANT, ACCESS_TOKEN);
        expectGroupsEndpoint("{\"value\": []}");

        // when
        var result = adapter.listGroups("default", DEFAULT_IDP);

        // then
        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Token: Bearer header is passed to Graph calls
    // -------------------------------------------------------------------------

    @Test
    void listGroups_passesServiceAccountTokenAsBearer() {

        // given
        EntraGraph.expectToken(server, DEFAULT_TENANT, ACCESS_TOKEN);
        server.expect(requestTo(GROUPS_ENDPOINT)).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andRespond(withSuccess("{\"value\": []}", MediaType.APPLICATION_JSON));

        // when
        adapter.listGroups("default", DEFAULT_IDP);

        // then
        server.verify();
    }

    // -------------------------------------------------------------------------
    // Paging: follows @odata.nextLink until exhausted
    // -------------------------------------------------------------------------

    @Test
    void listGroups_pagination_followsNextLinkAndAccumulatesGroups() {

        // given
        EntraGraph.expectToken(server, DEFAULT_TENANT, ACCESS_TOKEN);
        var page2Url = GROUPS_ENDPOINT + "&$skiptoken=page2token";

        // Page 1: has nextLink pointing at page 2
        server.expect(requestTo(GROUPS_ENDPOINT)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(pagedJson("""
                {"id": "g1", "displayName": "Group One"}
                """, page2Url), MediaType.APPLICATION_JSON));
        // Page 2: no nextLink
        server.expect(requestTo(page2Url)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(singlePageJson("""
                {"id": "g2", "displayName": "Group Two"}
                """), MediaType.APPLICATION_JSON));

        // when
        var result = adapter.listGroups("default", DEFAULT_IDP);

        // then
        server.verify();
        assertThat(result).hasSize(2);
        assertThat(result).extracting(IdpGroup::groupId).containsExactly("g1", "g2");
        assertThat(result).extracting(IdpGroup::displayName).containsExactly("Group One", "Group Two");
    }

    @Test
    void listGroups_noPaging_singlePageReturnsAllGroups() {

        // given
        EntraGraph.expectToken(server, DEFAULT_TENANT, ACCESS_TOKEN);
        expectGroupsEndpoint(singlePageJson("""
                {"id": "g1", "displayName": "Only Group"}
                """));

        // when
        var result = adapter.listGroups("default", DEFAULT_IDP);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().groupId()).isEqualTo("g1");
    }

    // -------------------------------------------------------------------------
    // Provider key
    // -------------------------------------------------------------------------

    @Test
    void provider_returnsEntra() {

        // then
        assertThat(adapter.provider()).isEqualTo(IdpProvider.ENTRA);
    }

    // -------------------------------------------------------------------------
    // Tenant isolation: each Organization's groups come from its own Entra tenant
    // -------------------------------------------------------------------------

    @Test
    void listGroups_twoOrganizations_queriesEachOwnTenant() {

        // given - two Organizations on two different Entra tenants, each with its own access token
        EntraGraph.expectToken(server, "tenant-acme", "token-acme");
        server.expect(requestTo(GROUPS_ENDPOINT)).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-acme")).andRespond(withSuccess(singlePageJson("""
                        {"id": "acme-g1", "displayName": "Acme Leads"}
                        """), MediaType.APPLICATION_JSON));
        EntraGraph.expectToken(server, "tenant-globex", "token-globex");
        server.expect(requestTo(GROUPS_ENDPOINT)).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-globex")).andRespond(withSuccess(singlePageJson("""
                        {"id": "globex-g1", "displayName": "Globex Leads"}
                        """), MediaType.APPLICATION_JSON));

        // when
        var acme = adapter.listGroups("acme", new OrganizationIdp(IdpProvider.ENTRA, "tenant-acme", true));
        var globex = adapter.listGroups("globex", new OrganizationIdp(IdpProvider.ENTRA, "tenant-globex", true));

        // then - each call minted a token for its own tenant and carried it to Graph
        server.verify();
        assertThat(acme).extracting(IdpGroup::displayName).containsExactly("Acme Leads");
        assertThat(globex).extracting(IdpGroup::displayName).containsExactly("Globex Leads");
    }

    @Test
    void listGroups_organizationWithoutEntraTenant_returnsEmptyWithoutCallingGraph() {

        // given - a connection with no aadTenantId has no tenant to query
        var idp = new OrganizationIdp(IdpProvider.ENTRA, null, false);

        // when
        var actual = adapter.listGroups("a-hub", idp);

        // then
        assertThat(actual).isEmpty();
        server.verify();
    }

    @Test
    void listGroups_organizationThatHasNotProvedItOwnsTheTenant_returnsEmptyWithoutCallingGraph() {

        // given - a real, well-formed tenant, typed by an administrator and agreeing with the connection's issuer,
        // which is all the earlier checks ever asked for. Nobody has shown the Organization owns it.
        var idp = new OrganizationIdp(IdpProvider.ENTRA, "tenant-somebody-else", false);

        // when
        var actual = adapter.listGroups("acme", idp);

        // then - not merely an empty answer: no token is minted and no Graph request leaves the process
        assertThat(actual).isEmpty();
        server.verify();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void expectGroupsEndpoint(String body) {
        server.expect(requestTo(GROUPS_ENDPOINT)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    /** Wraps group object JSON fragments in a Graph page response with no nextLink. */
    private static String singlePageJson(String groupsJson) {
        return "{\"value\": [" + groupsJson + "]}";
    }

    /** Wraps group object JSON fragments in a Graph page response with a nextLink. */
    private static String pagedJson(String groupsJson, String nextLink) {
        return "{\"value\": [" + groupsJson + "], \"@odata.nextLink\": \"" + nextLink + "\"}";
    }
}
