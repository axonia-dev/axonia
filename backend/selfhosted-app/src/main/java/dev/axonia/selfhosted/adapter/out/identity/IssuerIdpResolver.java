package dev.axonia.selfhosted.adapter.out.identity;

import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.axonia.identity.api.IdpProvider;
import dev.axonia.identity.api.OrganizationIdp;
import dev.axonia.identity.api.spi.OrganizationIdpResolver;
import dev.axonia.selfhosted.configuration.SelfHostedProperties;
import dev.axonia.selfhosted.domain.EntraIssuer;

/**
 * The installation's one issuer is its Organization's identity provider. Only an Entra issuer says which provider it
 * is; Graph is still read only when the Entra client credentials are configured. Reads as allowed because the operator
 * chose this tenant as the only one whose tokens are accepted.
 */
@Component
class IssuerIdpResolver implements OrganizationIdpResolver {

    private final String organizationId;

    private final Optional<OrganizationIdp> idp;

    IssuerIdpResolver(SelfHostedProperties properties) {
        this.organizationId = properties.organization().id();
        this.idp = EntraIssuer.tenantOf(properties.oidc().issuerUri())
                .map(tenant -> new OrganizationIdp(IdpProvider.ENTRA, tenant, true));
    }

    @Override
    public Optional<OrganizationIdp> resolve(String organizationId) {
        return this.organizationId.equals(organizationId) ? idp : Optional.empty();
    }
}
