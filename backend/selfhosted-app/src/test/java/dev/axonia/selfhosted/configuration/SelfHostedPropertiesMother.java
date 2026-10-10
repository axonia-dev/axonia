package dev.axonia.selfhosted.configuration;

import java.util.List;

import dev.axonia.selfhosted.MockOidcIssuer;
import dev.axonia.selfhosted.SelfHostedTestInfrastructure;

/**
 * Self-hosted settings as the test infrastructure configures them, with the issuer or the subject claim as the one
 * variable.
 */
public final class SelfHostedPropertiesMother {

    public static final String ENTRA_TENANT = "4f1c5c43-6a3b-4c8e-9d2a-0b7e5f9a1c22";

    public static final String GENERIC_ISSUER = "https://login.example.test/realms/acme";

    private SelfHostedPropertiesMother() {
    }

    public static SelfHostedProperties issuedBy(String issuerUri) {

        return configured(issuerUri, "sub");
    }

    public static SelfHostedProperties matchingAccountsOn(String subjectClaim) {

        return configured(GENERIC_ISSUER, subjectClaim);
    }

    public static SelfHostedProperties any() {

        return issuedBy(GENERIC_ISSUER);
    }

    private static SelfHostedProperties configured(String issuerUri, String subjectClaim) {

        return new SelfHostedProperties(
                new SelfHostedProperties.Organization(SelfHostedTestInfrastructure.ORGANIZATION_ID,
                        SelfHostedTestInfrastructure.ORGANIZATION_NAME),
                SelfHostedTestInfrastructure.OWNER_GROUP,
                new SelfHostedProperties.Oidc(issuerUri, MockOidcIssuer.AUDIENCE, subjectClaim, "groups",
                        List.of("email", "preferred_username"), "name"));
    }
}
