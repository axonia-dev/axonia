package dev.axonia.selfhosted.internal;

import org.junit.jupiter.api.Test;

import dev.axonia.identity.IdpProvider;
import dev.axonia.identity.OrganizationIdp;

import static dev.axonia.selfhosted.internal.SelfHostedPropertiesMother.ENTRA_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

class IssuerIdpResolverTest {

    @Test
    void anEntraV2IssuerIsTheOrganizationsEntraTenantWithGraphReadsAllowed() {

        // given
        var resolver = new IssuerIdpResolver(
                SelfHostedPropertiesMother.issuedBy("https://login.microsoftonline.com/" + ENTRA_TENANT + "/v2.0"));

        // when
        var actual = resolver.resolve("acme");

        // then
        assertThat(actual).contains(new OrganizationIdp(IdpProvider.ENTRA, ENTRA_TENANT, true));
    }

    @Test
    void anEntraV1IssuerIsTheSameTenant() {

        // given
        var resolver = new IssuerIdpResolver(
                SelfHostedPropertiesMother.issuedBy("https://sts.windows.net/" + ENTRA_TENANT + "/"));

        // when
        var actual = resolver.resolve("acme");

        // then
        assertThat(actual).contains(new OrganizationIdp(IdpProvider.ENTRA, ENTRA_TENANT, true));
    }

    @Test
    void anyOtherIssuerResolvesNoIdentityProvider() {

        // given
        var resolver = new IssuerIdpResolver(SelfHostedPropertiesMother.any());

        // when
        var actual = resolver.resolve("acme");

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void anotherOrganizationResolvesNothingEvenOnEntra() {

        // given
        var resolver = new IssuerIdpResolver(
                SelfHostedPropertiesMother.issuedBy("https://login.microsoftonline.com/" + ENTRA_TENANT + "/v2.0"));

        // when
        var actual = resolver.resolve("globex");

        // then
        assertThat(actual).isEmpty();
    }
}
