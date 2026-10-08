package dev.axonia.organization.internal;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import dev.axonia.OrganizationResolver;
import dev.axonia.organization.Organization;
import dev.axonia.organization.OrganizationKind;
import dev.axonia.organization.OrganizationLifecycleState;
import dev.axonia.organization.OrganizationService;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizationLifecycleFilterTest {

    private static final String ORG = "acme";

    private final OrganizationService organizations = mock(OrganizationService.class);
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    private OrganizationLifecycleFilter filterResolving(String organizationId) {
        OrganizationResolver resolver = () -> Optional.ofNullable(organizationId);
        return new OrganizationLifecycleFilter(resolver, organizations, JsonMapper.builder().build());
    }

    private void registryHolds(OrganizationLifecycleState state) {
        when(organizations.organization(ORG)).thenReturn(Optional.of(Organization.builder().id(ORG).name("Acme")
                .kind(OrganizationKind.ENTERPRISE).lifecycleState(state).build()));
    }

    @Test
    void passesThroughWhenNoOrganizationIsResolved() throws Exception {

        filterResolving(null).doFilter(new MockHttpServletRequest("GET", "/axonia/health"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void passesThroughAnActiveOrganization() throws Exception {
        registryHolds(OrganizationLifecycleState.ACTIVE);

        filterResolving(ORG).doFilter(new MockHttpServletRequest("GET", "/axonia/me"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void refusesASuspendedOrganizationWithItsCode() throws Exception {
        registryHolds(OrganizationLifecycleState.SUSPENDED);

        filterResolving(ORG).doFilter(new MockHttpServletRequest("GET", "/axonia/me"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"code\":\"organization_suspended\"");
    }

    @Test
    void refusesAClosedOrganizationWithItsOwnCode() throws Exception {
        registryHolds(OrganizationLifecycleState.CLOSED);

        filterResolving(ORG).doFilter(new MockHttpServletRequest("GET", "/axonia/me"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"organization_closed\"");
    }

    @Test
    void rejectsATokenNamingAnUnknownOrganization() throws Exception {
        when(organizations.organization(ORG)).thenReturn(Optional.empty());

        filterResolving(ORG).doFilter(new MockHttpServletRequest("GET", "/axonia/me"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"code\":\"unknown_organization\"");
    }

    @Test
    void rejectsAScimCallerNamingAnUnknownOrganizationInScimsOwnErrorSchema() throws Exception {
        when(organizations.organization(ORG)).thenReturn(Optional.empty());

        filterResolving(ORG).doFilter(new MockHttpServletRequest("GET", "/scim/v2/Users"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/scim+json");
        assertThat(response.getContentAsString()).contains("urn:ietf:params:scim:api:messages:2.0:Error")
                .contains("\"code\":\"unknown_organization\"");
    }

    /**
     * A signed-in member of a suspended Organization must reach the link-authorized surface a total stranger already
     * reaches. Nothing there is decided by the caller's Organization, so refusing them would take an invitation to a
     * perfectly healthy Community Hub away from somebody whose own account happens to be off.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/public/hubs", "/public/hubs/rust-practitioners", "/public/invites/abc123"})
    void letsASuspendedMemberReachThePublicSurfaceAnAnonymousCallerAlreadyReaches(String path) throws Exception {
        registryHolds(OrganizationLifecycleState.SUSPENDED);

        filterResolving(ORG).doFilter(new MockHttpServletRequest("GET", path), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void answersScimCallersInScimsOwnErrorSchema() throws Exception {
        registryHolds(OrganizationLifecycleState.SUSPENDED);

        filterResolving(ORG).doFilter(new MockHttpServletRequest("GET", "/scim/v2/Users"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/scim+json");
        assertThat(response.getContentAsString()).contains("urn:ietf:params:scim:api:messages:2.0:Error")
                .contains("\"code\":\"organization_suspended\"");
    }
}
