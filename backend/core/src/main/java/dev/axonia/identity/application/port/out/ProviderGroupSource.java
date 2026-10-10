package dev.axonia.identity.application.port.out;

import java.util.List;

import dev.axonia.identity.api.IdentityProviderDirectory;
import dev.axonia.identity.api.IdpProvider;
import dev.axonia.identity.api.OrganizationIdp;
import dev.axonia.identity.application.RoutingIdentityProviderDirectory;

/**
 * Internal seam used by the routing layer to dispatch group-listing calls to the correct identity provider backend
 * (e.g. Microsoft Entra).
 *
 * <p>
 * Each implementation declares the {@link IdpProvider} it backs, and the router dispatches on that enum.
 *
 * <p>
 * The Organization's resolved {@link OrganizationIdp} is passed in rather than re-resolved here: the router resolves it
 * once, so there is a single place that decides which customer tenant a call targets.
 *
 * <p>
 * Implementations of this interface should NOT also implement {@link IdentityProviderDirectory} directly - the single
 * {@link RoutingIdentityProviderDirectory} bean owns that contract.
 */
public interface ProviderGroupSource {

    /** The IdP vendor this source backs. */
    IdpProvider provider();

    List<IdentityProviderDirectory.IdpGroup> listGroups(String organizationId, OrganizationIdp idp);
}
