package dev.axonia.shared.api.tenancy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ProvisioningAuthenticationTest {

    @Test
    void namesTheOrganizationTheWorkRunsIn() {
        var authentication = new ProvisioningAuthentication("northwind");

        assertThat(authentication.organizationId()).isEqualTo("northwind");
    }

    @Test
    void isAuthenticatedButGrantsNoAuthorities() {
        var authentication = new ProvisioningAuthentication("northwind");

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    void carriesNoCredentialsAndASystemPrincipal() {
        var authentication = new ProvisioningAuthentication("northwind");

        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getPrincipal()).isEqualTo("provisioning");
    }
}
