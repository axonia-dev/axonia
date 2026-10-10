package dev.axonia.identity.adapter.out.entra;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.identity.api.IdentityProviderDirectory;
import dev.axonia.identity.api.IdpProvider;
import dev.axonia.identity.api.OrganizationIdp;
import dev.axonia.identity.application.port.out.ProviderGroupSource;

/**
 * Microsoft Entra (Azure AD / Microsoft Graph) backed {@link ProviderGroupSource}.
 *
 * <p>
 * The tenant queried is the {@code aadTenantId} on the Organization's own SSO connection, carried in on the
 * {@link OrganizationIdp} the router resolved. One deployment federates many customers, so an Organization must only
 * ever see groups from its own tenant. A connection without an {@code aadTenantId} has no tenant to query and yields no
 * groups rather than falling back to somebody else's.
 *
 * <p>
 * A tenant the Organization has not proved it owns yields no groups either, and without a Graph call being made. The
 * {@code aadTenantId} is typed by an administrator and axonia's app registration is multi-tenant, so naming somebody
 * else's directory is enough to read it; a proof of ownership is what turns the claim into a fact, and
 * {@link OrganizationIdp#graphReadsAllowed()} carries its verdict here.
 *
 * <p>
 * The read itself is {@link GraphGroups}', which holds Graph's answer for five minutes per tenant. This check stays in
 * front of that cache, so a revoked proof takes effect at once however recently the tenant was read.
 *
 * <p>
 * <strong>Deployment prerequisite:</strong> the app registration configured in {@code axonia.idp.entra.*} must be
 * multi-tenant and hold the {@code GroupMember.Read.All} application permission (not delegated) in Microsoft Graph,
 * with admin consent granted in each customer tenant.
 *
 * <p>
 * <strong>Discovery only - never on the auth path.</strong>
 */
@Component
class EntraDirectoryAdapter implements ProviderGroupSource {

    private final GraphGroups graphGroups;

    EntraDirectoryAdapter(GraphGroups graphGroups) {
        this.graphGroups = graphGroups;
    }

    @Override
    public IdpProvider provider() {
        return IdpProvider.ENTRA;
    }

    @Override
    public List<IdentityProviderDirectory.IdpGroup> listGroups(String organizationId, OrganizationIdp idp) {
        if (idp.aadTenantId() == null || !idp.graphReadsAllowed()) {
            return List.of();
        }
        return graphGroups.listFor(idp.aadTenantId());
    }
}
