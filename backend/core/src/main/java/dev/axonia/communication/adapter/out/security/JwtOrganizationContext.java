package dev.axonia.communication.adapter.out.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import dev.axonia.communication.application.port.out.OrganizationContext;
import dev.axonia.shared.api.security.CurrentToken;
import dev.axonia.shared.api.tenancy.OrganizationBoundAuthentication;

/**
 * Resolves the organization a request is scoped to. Checks {@link OrganizationBoundAuthentication} first, exactly like
 * the shared-kernel {@code shared.configuration.PersistenceConfig.organizationResolver()} does for the same reason:
 * this class is not only ever reached from an authenticated HTTP request. {@code JdbcOutbox.enqueue} is also called by
 * the communication sweep's producers (e.g. {@code events}' {@code SessionProducers}), which run inside
 * {@code OrganizationScope.runAs} - a {@code ProvisioningAuthentication}, not a {@code JwtAuthenticationToken} - and
 * {@link CurrentToken#organization()} throws {@code IllegalStateException} for any authentication that is not a JWT.
 * Falling back to {@link CurrentToken#organization()} keeps a real HTTP caller (an admin resend, say) working exactly
 * as before.
 */
@Component("communicationJwtOrganizationContext")
public class JwtOrganizationContext implements OrganizationContext {
    @Override
    public String organizationId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof OrganizationBoundAuthentication oba) {
            return oba.organizationId();
        }
        return CurrentToken.organization();
    }
}
