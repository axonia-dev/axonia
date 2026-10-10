package dev.axonia.selfhosted.adapter.out.identity;

import org.junit.jupiter.api.Test;

import dev.axonia.selfhosted.configuration.SelfHostedPropertiesMother;

import static dev.axonia.selfhosted.configuration.SelfHostedPropertiesMother.ENTRA_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

class IssuerSignInProviderTest {

    /** {@code GET /me} asks with the token's Organization, and with {@code null} for a token naming none. */
    @Test
    void anEntraIssuerIsLabelledMicrosoftEntraIdWhicheverOrganizationIsAsked() {

        // given
        var provider = new IssuerSignInProvider(
                SelfHostedPropertiesMother.issuedBy("https://login.microsoftonline.com/" + ENTRA_TENANT + "/v2.0"));

        // when
        var actualForTheOrganization = provider.resolve("acme");
        var actualForNone = provider.resolve(null);

        // then
        assertThat(actualForTheOrganization).isEqualTo("Microsoft Entra ID");
        assertThat(actualForNone).isEqualTo("Microsoft Entra ID");
    }

    @Test
    void anyOtherIssuerHasNoLabel() {

        // given
        var provider = new IssuerSignInProvider(SelfHostedPropertiesMother.any());

        // when
        var actual = provider.resolve("acme");

        // then
        assertThat(actual).isNull();
    }
}
