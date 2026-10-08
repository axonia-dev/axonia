package dev.axonia.selfhosted.internal;

import org.springframework.stereotype.Component;

import dev.axonia.identity.SignInProviderResolver;
import dev.axonia.selfhosted.SelfHostedProperties;

/**
 * Names the company's identity provider on the account page when its issuer says which one it is. Any other issuer has
 * no name worth showing to a member who has one way in, so the page shows none.
 */
@Component
class IssuerSignInProvider implements SignInProviderResolver {

    private static final String ENTRA = "Microsoft Entra ID";

    private final String label;

    IssuerSignInProvider(SelfHostedProperties properties) {
        this.label = EntraIssuer.tenantOf(properties.oidc().issuerUri()).map(tenant -> ENTRA).orElse(null);
    }

    @Override
    public String resolve(String organizationId) {
        return label;
    }
}
