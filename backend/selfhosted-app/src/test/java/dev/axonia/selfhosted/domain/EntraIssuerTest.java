package dev.axonia.selfhosted.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.axonia.selfhosted.configuration.SelfHostedPropertiesMother;

import static dev.axonia.selfhosted.configuration.SelfHostedPropertiesMother.ENTRA_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

class EntraIssuerTest {

    @ParameterizedTest
    @ValueSource(strings = {"https://login.microsoftonline.com/" + ENTRA_TENANT + "/v2.0",
            "https://login.microsoftonline.com/" + ENTRA_TENANT + "/v2.0/",
            "https://sts.windows.net/" + ENTRA_TENANT + "/", "https://sts.windows.net/" + ENTRA_TENANT})
    void readsTheTenantOfAWorkforceTenantsV2OrV1Issuer(String issuerUri) {

        // when
        var actual = EntraIssuer.tenantOf(issuerUri);

        // then
        assertThat(actual).contains(ENTRA_TENANT);
    }

    /**
     * A multi-tenant authority names no tenant, a sovereign cloud reads Graph elsewhere than axonia calls it, and
     * anything else that merely starts or ends like an Entra issuer is not one.
     */
    @ParameterizedTest
    @ValueSource(strings = {"https://login.microsoftonline.com/common/v2.0",
            "https://login.microsoftonline.com/organizations/v2.0",
            "https://login.microsoftonline.us/" + ENTRA_TENANT + "/v2.0",
            "https://login.microsoftonline.com/" + ENTRA_TENANT,
            "https://login.microsoftonline.com/" + ENTRA_TENANT + "/v2.0/extra",
            "http://login.microsoftonline.com/" + ENTRA_TENANT + "/v2.0",
            "https://login.microsoftonline.com.example.test/" + ENTRA_TENANT + "/v2.0",
            "https://example.test/https://sts.windows.net/" + ENTRA_TENANT + "/",
            SelfHostedPropertiesMother.GENERIC_ISSUER})
    void readsNoTenantFromAnyOtherIssuer(String issuerUri) {

        // when
        var actual = EntraIssuer.tenantOf(issuerUri);

        // then
        assertThat(actual).isEmpty();
    }
}
