package dev.axonia.identity.application;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.axonia.identity.api.GroupSource;
import dev.axonia.identity.api.IdentityProviderDirectory;
import dev.axonia.identity.api.IdpProvider;
import dev.axonia.identity.api.OrganizationIdp;
import dev.axonia.identity.api.spi.OrganizationIdpResolver;
import dev.axonia.identity.application.port.out.ProviderGroupSource;

/**
 * The single {@link IdentityProviderDirectory} bean.
 *
 * <p>
 * {@link #listGroups} resolves suggestions in order: a synced SCIM directory wins outright when it has groups for the
 * Organization; otherwise an Entra Organization gets a live Microsoft Graph listing, but only when axonia's own Graph
 * app registration is configured, and it degrades to no suggestions - never a failed page - both when Graph is
 * unconfigured and when the live call itself fails; every other Organization is offered no suggestions at all and
 * authors its bindings by value, the baseline kept underneath every source.
 *
 * <p>
 * There is deliberately no source for the application's own identity provider. It is a deployment-level coordinate
 * shared by every tenant, not any customer's directory: a customer's groups reach axonia only in the token's
 * {@code groups} claim, and the application's own provider never mirrors them. Listing it would offer every
 * Organization the same platform-internal group names, which is what it did until the fallback was removed.
 *
 * <p>
 * This is the single place the Organization's IdP is resolved. The whole {@link OrganizationIdp} is handed to the
 * source, so a provider-specific coordinate such as the Entra {@code aadTenantId} reaches the Graph call without a
 * second lookup that could disagree with the routing decision made here.
 *
 * <p>
 * <strong>Discovery only - never on the auth path.</strong>
 */
@Component
public class RoutingIdentityProviderDirectory implements IdentityProviderDirectory {

    private static final Logger log = LoggerFactory.getLogger(RoutingIdentityProviderDirectory.class);

    private static final OrganizationIdp NO_CONNECTION = new OrganizationIdp(IdpProvider.GENERIC_OIDC, null, false);

    private final SyncedDirectoryGroupSource syncedSource;
    private final Map<IdpProvider, ProviderGroupSource> sources;
    private final OrganizationIdpResolver resolver;
    private final GraphAppRegistration graphProperties;

    RoutingIdentityProviderDirectory(SyncedDirectoryGroupSource syncedSource, List<ProviderGroupSource> sources,
            OrganizationIdpResolver resolver, GraphAppRegistration graphProperties) {
        this.syncedSource = syncedSource;
        this.sources = sources.stream().collect(Collectors.toMap(ProviderGroupSource::provider, Function.identity()));
        this.resolver = resolver;
        this.graphProperties = graphProperties;
    }

    @Override
    public DirectoryListing listGroups(String organizationId) {
        var scim = syncedSource.listGroups(organizationId);
        if (!scim.isEmpty()) {
            return new DirectoryListing(GroupSource.SCIM, scim);
        }
        var idp = resolver.resolve(organizationId).orElse(NO_CONNECTION);
        if (idp.provider().federatesMicrosoftDirectory()) {
            return entraOrNone(organizationId, idp);
        }
        return DirectoryListing.none();
    }

    /**
     * {@code none()} rather than an empty GRAPH listing whenever Graph will not be read, and the distinction is the
     * whole point of {@link GroupSource#NONE}: a GRAPH listing tells the UI the directory was read and holds no groups,
     * which withdraws the direct-value-entry affordance the page falls back to. An unproved tenant belongs with an
     * unconfigured Graph app and a failed call, not with an empty directory - in all three the answer is "no
     * suggestions from here", never "there are none".
     */
    private DirectoryListing entraOrNone(String organizationId, OrganizationIdp idp) {
        if (!graphProperties.isConfigured() || !idp.graphReadsAllowed()) {
            return DirectoryListing.none();
        }
        // IdpProvider.ENTRA rather than federatesMicrosoftDirectory(), five lines after listGroups asked the
        // predicate: this is a map key, matched against the value EntraDirectoryAdapter.provider() registers itself
        // under, not a capability test. A predicate cannot select from a map keyed by vendor.
        // A second value answering the predicate would be routed here by listGroups and then handed Entra's own Graph
        // source, or throw naming a provider the caller never sent. Adopting it means registering its own
        // ProviderGroupSource and choosing between the two here, not widening this lookup.
        var source = sources.get(IdpProvider.ENTRA);
        if (source == null) {
            throw new IllegalStateException("No ProviderGroupSource registered for provider 'ENTRA' "
                    + "(organization='" + organizationId + "')");
        }
        try {
            return new DirectoryListing(GroupSource.GRAPH, source.listGroups(organizationId, idp));
        } catch (RuntimeException e) {
            log.warn(
                    "Microsoft Graph group discovery failed for Organization {} (tenant {}); "
                            + "the Group access page falls back to direct value entry",
                    organizationId, idp.aadTenantId(), e);
            return DirectoryListing.none();
        }
    }
}
