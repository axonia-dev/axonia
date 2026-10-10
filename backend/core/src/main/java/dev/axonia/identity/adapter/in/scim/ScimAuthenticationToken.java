package dev.axonia.identity.adapter.in.scim;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import dev.axonia.identity.application.scim.ScimPrincipal;
import dev.axonia.shared.api.tenancy.OrganizationBoundAuthentication;

/**
 * Spring Security token set into the {@link org.springframework.security.core.context.SecurityContext} by
 * {@link ScimTokenAuthenticationFilter} after a valid SCIM bearer token is verified. Implementing
 * {@link OrganizationBoundAuthentication} lets the shared {@code PersistenceConfig.organizationResolver()} extract the
 * organization for RLS without importing anything from the {@code identity} module.
 */
public final class ScimAuthenticationToken extends AbstractAuthenticationToken
        implements
            OrganizationBoundAuthentication {

    private final ScimPrincipal principal;

    public ScimAuthenticationToken(ScimPrincipal principal) {
        super(List.of()); // SCIM: "authenticated == provisioned for this org" — no granted authorities needed
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null; // bearer token is not stored after authentication
    }

    @Override
    public ScimPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String organizationId() {
        return principal.organizationId();
    }
}
