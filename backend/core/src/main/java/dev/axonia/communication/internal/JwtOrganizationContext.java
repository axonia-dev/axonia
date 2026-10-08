package dev.axonia.communication.internal;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import dev.axonia.CurrentToken;
import dev.axonia.OrganizationBoundAuthentication;
import dev.axonia.communication.OrganizationContext;

/**
 * Resolves the organization a request is scoped to. Checks {@link OrganizationBoundAuthentication} first, exactly like
 * the shared-kernel {@code config.PersistenceConfig.organizationResolver()} does for the same reason: this class is not
 * only ever reached from an authenticated HTTP request. {@link JdbcOutbox#enqueue} is also called by the communication
 * sweep's producers (e.g. {@code events}' {@code SessionProducers}), which run inside {@code OrganizationScope.runAs} -
 * a {@code ProvisioningAuthentication}, not a {@code JwtAuthenticationToken} - and {@link CurrentToken#organization()}
 * throws {@code IllegalStateException} for any authentication that is not a JWT. Falling back to
 * {@link CurrentToken#organization()} keeps a real HTTP caller (an admin resend, say) working exactly as before.
 */
@Component("communicationJwtOrganizationContext")
class JwtOrganizationContext implements OrganizationContext {
    @Override
    public String organizationId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof OrganizationBoundAuthentication oba) {
            return oba.organizationId();
        }
        return CurrentToken.organization();
    }
}
