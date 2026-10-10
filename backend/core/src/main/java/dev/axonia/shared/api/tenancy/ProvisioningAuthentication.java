package dev.axonia.shared.api.tenancy;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * The authentication a system-initiated action carries when there is no request and no JWT, such as hub provisioning or
 * operator provisioning. It grants no authorities: its only job is to name the Organization whose RLS scope the work
 * runs in, via {@link OrganizationBoundAuthentication}, which {@code PersistenceConfig.organizationResolver()} reads
 * when the transaction acquires its connection.
 */
public final class ProvisioningAuthentication extends AbstractAuthenticationToken
        implements
            OrganizationBoundAuthentication {

    private final String organizationId;

    public ProvisioningAuthentication(String organizationId) {
        super(List.of());
        this.organizationId = organizationId;
        setAuthenticated(true);
    }

    @Override
    public String organizationId() {
        return organizationId;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return "provisioning";
    }
}
