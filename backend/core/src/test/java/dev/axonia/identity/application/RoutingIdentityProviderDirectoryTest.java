package dev.axonia.identity.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.api.GroupSource;
import dev.axonia.identity.api.IdentityProviderDirectory.IdpGroup;
import dev.axonia.identity.api.IdpProvider;
import dev.axonia.identity.api.OrganizationIdp;
import dev.axonia.identity.api.spi.OrganizationIdpResolver;
import dev.axonia.identity.application.port.out.ProviderGroupSource;
import dev.axonia.identity.configuration.EntraGraphProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class RoutingIdentityProviderDirectoryTest {

    private static final IdpGroup ENTRA_GROUP = new IdpGroup("entra-1", "Entra Group");

    @Mock
    private OrganizationIdpResolver resolver;

    @Mock
    private SyncedDirectoryGroupSource syncedSource;

    @Mock
    private EntraGraphProperties graphProperties;

    @BeforeEach
    void defaults() {
        when(syncedSource.listGroups(any())).thenReturn(List.of());
        lenient().when(graphProperties.isConfigured()).thenReturn(true);
    }

    private final List<OrganizationIdp> entraCalls = new ArrayList<>();

    /**
     * A source for a provider with no directory to read, which fails the test if it is ever consulted. Only a
     * Microsoft-federating connection gives axonia a customer directory to enumerate; for anything else the only realm
     * left to read is axonia's own, a deployment-level coordinate shared by every tenant that is never a customer's
     * directory. Registered here rather than simply omitted so that re-introducing the fallback fails loudly.
     */
    private ProviderGroupSource sourceWithNoDirectoryThatMustNeverBeCalled() {

        return new ProviderGroupSource() {
            @Override
            public IdpProvider provider() {
                return IdpProvider.GENERIC_OIDC;
            }

            @Override
            public List<IdpGroup> listGroups(String org, OrganizationIdp idp) {
                throw new AssertionError("axonia's own realm must never be offered as a directory: " + org);
            }
        };
    }

    private ProviderGroupSource entraSource() {

        return new ProviderGroupSource() {
            @Override
            public IdpProvider provider() {
                return IdpProvider.ENTRA;
            }

            @Override
            public List<IdpGroup> listGroups(String org, OrganizationIdp idp) {
                entraCalls.add(idp);
                return List.of(new IdpGroup(org + "-" + idp.aadTenantId(), ENTRA_GROUP.displayName()));
            }
        };
    }

    @Test
    void listGroups_entraOrganization_usesTheEntraSource() {

        // given
        when(resolver.resolve("acme"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, "tenant-1", true)));
        var sut = new RoutingIdentityProviderDirectory(syncedSource,
                List.of(sourceWithNoDirectoryThatMustNeverBeCalled(), entraSource()), resolver, graphProperties);

        // when
        var actual = sut.listGroups("acme");

        // then
        assertThat(actual.groups()).extracting(IdpGroup::displayName).containsExactly(ENTRA_GROUP.displayName());
    }

    @Test
    void listGroups_twoEntraOrganizations_passesEachOwnTenantToTheSource() {

        // given - two customers federated by the same deployment, on two different Entra tenants
        when(resolver.resolve("acme"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, "tenant-acme", true)));
        when(resolver.resolve("globex"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, "tenant-globex", true)));
        var sut = new RoutingIdentityProviderDirectory(syncedSource,
                List.of(sourceWithNoDirectoryThatMustNeverBeCalled(), entraSource()), resolver, graphProperties);

        // when
        var acme = sut.listGroups("acme");
        var globex = sut.listGroups("globex");

        // then - the source saw each Organization's own tenant, never the other's
        assertThat(entraCalls).extracting(OrganizationIdp::aadTenantId).containsExactly("tenant-acme", "tenant-globex");
        assertThat(acme.groups()).extracting(IdpGroup::groupId).containsExactly("acme-tenant-acme");
        assertThat(globex.groups()).extracting(IdpGroup::groupId).containsExactly("globex-tenant-globex");
    }

    @Test
    void listGroups_organizationWithoutConnection_offersNoSuggestions() {

        // given - the normal case for a hub, which never has an SSO connection
        when(resolver.resolve("a-hub")).thenReturn(Optional.empty());
        var sut = new RoutingIdentityProviderDirectory(syncedSource,
                List.of(sourceWithNoDirectoryThatMustNeverBeCalled(), entraSource()), resolver, graphProperties);

        // when
        var listing = sut.listGroups("a-hub");

        // then
        assertThat(listing.source()).isEqualTo(GroupSource.NONE);
        assertThat(listing.groups()).isEmpty();
    }

    @Test
    void listGroups_organizationWithNoDirectoryToRead_offersNoSuggestions() {

        // given - a customer whose identity provider is federated into axonia's. Their groups reach axonia only in the
        // token claim; axonia's own identity provider never mirrors them, so it has nothing to suggest
        when(resolver.resolve("acme"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.GENERIC_OIDC, null, false)));
        var sut = new RoutingIdentityProviderDirectory(syncedSource,
                List.of(sourceWithNoDirectoryThatMustNeverBeCalled(), entraSource()), resolver, graphProperties);

        // when
        var listing = sut.listGroups("acme");

        // then - direct value entry, the baseline that never goes away
        assertThat(listing.source()).isEqualTo(GroupSource.NONE);
        assertThat(listing.groups()).isEmpty();
    }

    @Test
    void listGroups_entraNoSourceRegistered_throws() {

        // given - Graph is configured, but no ENTRA ProviderGroupSource is wired; that is a wiring bug and
        // must hard-fail, unlike the fail-soft handling of an actual Graph HTTP call failure
        when(resolver.resolve("acme"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, "tenant-1", true)));
        var sut = new RoutingIdentityProviderDirectory(syncedSource,
                List.of(sourceWithNoDirectoryThatMustNeverBeCalled()), resolver, graphProperties);

        // when / then
        assertThatThrownBy(() -> sut.listGroups("acme")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ENTRA");
    }

    @Test
    void listGroups_entraWithoutGraphConfigured_degradesToNone() {

        // given - the Organization is on Entra, but axonia's own Graph app registration is not configured
        when(resolver.resolve("acme"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, "tenant-1", true)));
        when(graphProperties.isConfigured()).thenReturn(false);
        var sut = new RoutingIdentityProviderDirectory(syncedSource,
                List.of(sourceWithNoDirectoryThatMustNeverBeCalled(), entraSource()), resolver, graphProperties);

        // when
        var listing = sut.listGroups("acme");

        // then - no suggestions, and the Entra source is never even called
        assertThat(listing.source()).isEqualTo(GroupSource.NONE);
        assertThat(listing.groups()).isEmpty();
        assertThat(entraCalls).isEmpty();
    }

    /**
     * An unproved tenant belongs with an unconfigured Graph app and a failed call, not with an empty directory. A
     * {@code GRAPH} listing tells the page the directory was read and holds no groups, which is how it decides to stop
     * offering direct value entry - so reporting it here would leave an administrator with no way to name a group at
     * all, on the strength of a read that never happened.
     */
    @Test
    void listGroups_entraTenantNotProved_degradesToNoneRatherThanAnEmptyGraphListing() {

        // given - Graph is configured, the connection names a tenant, and nobody has proved the Organization owns it
        when(resolver.resolve("acme"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, "tenant-1", false)));
        var sut = new RoutingIdentityProviderDirectory(syncedSource,
                List.of(sourceWithNoDirectoryThatMustNeverBeCalled(), entraSource()), resolver, graphProperties);

        // when
        var listing = sut.listGroups("acme");

        // then - and the Entra source is never even called
        assertThat(listing.source()).isEqualTo(GroupSource.NONE);
        assertThat(listing.groups()).isEmpty();
        assertThat(entraCalls).isEmpty();
    }

    @Test
    void listGroups_entraGraphCallFails_degradesToNone() {

        // given - Graph is configured, but the call to Microsoft Graph itself fails
        when(resolver.resolve("acme"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, "tenant-1", true)));
        var failingEntraSource = new ProviderGroupSource() {
            @Override
            public IdpProvider provider() {
                return IdpProvider.ENTRA;
            }

            @Override
            public List<IdpGroup> listGroups(String org, OrganizationIdp idp) {
                throw new RuntimeException("401 Unauthorized");
            }
        };
        var sut = new RoutingIdentityProviderDirectory(syncedSource,
                List.of(sourceWithNoDirectoryThatMustNeverBeCalled(), failingEntraSource), resolver, graphProperties);

        // when
        var listing = sut.listGroups("acme");

        // then - the failure degrades the page rather than propagating as a 500
        assertThat(listing.source()).isEqualTo(GroupSource.NONE);
        assertThat(listing.groups()).isEmpty();
    }

    @Test
    void listGroups_prefersScimWhenDirectoryHasGroups() {

        // given - the SCIM directory already has groups for this organization
        when(syncedSource.listGroups("acme")).thenReturn(List.of(new IdpGroup("b7c1", "Platform")));
        var genericSource = mock(ProviderGroupSource.class);
        when(genericSource.provider()).thenReturn(IdpProvider.GENERIC_OIDC);
        var entraSource = mock(ProviderGroupSource.class);
        when(entraSource.provider()).thenReturn(IdpProvider.ENTRA);
        var sut = new RoutingIdentityProviderDirectory(syncedSource, List.of(genericSource, entraSource), resolver,
                graphProperties);

        // when
        var listing = sut.listGroups("acme");

        // then - the SCIM listing wins outright, no IdP provider is even consulted for groups
        assertThat(listing.source()).isEqualTo(GroupSource.SCIM);
        assertThat(listing.groups()).extracting(IdpGroup::groupId).containsExactly("b7c1");
        verify(genericSource, never()).listGroups(any(), any());
        verify(entraSource, never()).listGroups(any(), any());
    }
}
