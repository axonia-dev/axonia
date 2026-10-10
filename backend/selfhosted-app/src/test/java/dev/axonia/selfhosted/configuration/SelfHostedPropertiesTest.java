package dev.axonia.selfhosted.configuration;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SelfHostedConfiguration} is the configuration under test, because it is the one thing registering
 * {@link SelfHostedProperties} on the real context: a record never registered is a record whose constraints never run.
 */
class SelfHostedPropertiesTest {

    /** A name no environment variable can carry, so the placeholder is unresolvable by construction. */
    private static final String UNRESOLVABLE = "${no environment variable can be named this}";

    private static final Map<String, String> REQUIRED = Map.of("axonia.selfhosted.organization.id", "acme",
            "axonia.selfhosted.organization.name", "Acme", "axonia.selfhosted.owner-group", "axonia-owners",
            "axonia.selfhosted.oidc.issuer-uri", "https://login.example.com/tenant/v2.0",
            "axonia.selfhosted.oidc.audience", "11111111-2222-3333-4444-555555555555");

    @Test
    void bindsTheRequiredSettingsAndDefaultsTheClaims() {

        configured(REQUIRED).run(context -> {
            var properties = context.getBean(SelfHostedProperties.class);
            assertThat(properties.organization().id()).isEqualTo("acme");
            assertThat(properties.organization().name()).isEqualTo("Acme");
            assertThat(properties.ownerGroup()).isEqualTo("axonia-owners");
            assertThat(properties.oidc().issuerUri()).isEqualTo("https://login.example.com/tenant/v2.0");
            assertThat(properties.oidc().audience()).isEqualTo("11111111-2222-3333-4444-555555555555");
            assertThat(properties.oidc().subjectClaim()).isEqualTo("sub");
            assertThat(properties.oidc().groupsClaim()).isEqualTo("groups");
            assertThat(properties.oidc().emailClaims()).containsExactly("email", "preferred_username");
            assertThat(properties.oidc().nameClaim()).isEqualTo("name");
        });
    }

    @Test
    void bindsEveryClaimAnInstallationNames() {

        configured(REQUIRED).withPropertyValues("axonia.selfhosted.oidc.subject-claim=oid",
                "axonia.selfhosted.oidc.groups-claim=roles", "axonia.selfhosted.oidc.email-claims=upn,email",
                "axonia.selfhosted.oidc.name-claim=given_name").run(context -> {
                    var oidc = context.getBean(SelfHostedProperties.class).oidc();
                    assertThat(oidc.subjectClaim()).isEqualTo("oid");
                    assertThat(oidc.groupsClaim()).isEqualTo("roles");
                    assertThat(oidc.emailClaims()).containsExactly("upn", "email");
                    assertThat(oidc.nameClaim()).isEqualTo("given_name");
                });
    }

    /**
     * The validation error names a setting by its record components (its codes read
     * {@code NotBlank.axonia.selfhosted.ownerGroup}), not by the dashed name an operator writes.
     */
    @ParameterizedTest
    @CsvSource({"axonia.selfhosted.organization.id, organization.id",
            "axonia.selfhosted.organization.name, organization.name", "axonia.selfhosted.owner-group, ownerGroup",
            "axonia.selfhosted.oidc.issuer-uri, oidc.issuerUri", "axonia.selfhosted.oidc.audience, oidc.audience"})
    void refusesToStartWithoutARequiredSetting(String setting, String component) {

        var without = new HashMap<>(REQUIRED);
        without.remove(setting);

        configured(without).run(context -> assertRejected(context, component));
    }

    @Test
    void refusesToStartWithoutAnyOrganizationOrIssuer() {

        new ApplicationContextRunner().withUserConfiguration(SelfHostedConfiguration.class)
                .withPropertyValues("axonia.selfhosted.owner-group=axonia-owners").run(context -> {
                    assertRejected(context, "organization");
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("oidc");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"axonia.selfhosted.organization.name", "axonia.selfhosted.owner-group",
            "axonia.selfhosted.oidc.audience", "axonia.selfhosted.oidc.subject-claim",
            "axonia.selfhosted.oidc.groups-claim", "axonia.selfhosted.oidc.email-claims",
            "axonia.selfhosted.oidc.name-claim"})
    void refusesAnUnresolvedPlaceholder(String setting) {

        configured(REQUIRED).withPropertyValues(setting + "=" + UNRESOLVABLE)
                .run(context -> assertRejected(context, "must be a resolved value"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Acme", "acme_corp", "-acme", "acme-", "acme--corp", "acme corp", UNRESOLVABLE,
            "a234567890123456789012345678901234567890123456789012345678901234"})
    void refusesAnOrganizationIdThatIsNotAShortLowercaseSlug(String id) {

        configured(REQUIRED).withPropertyValues("axonia.selfhosted.organization.id=" + id)
                .run(context -> assertRejected(context, "organization.id"));
    }

    @Test
    void acceptsAnOrganizationIdOfLettersDigitsAndSingleHyphens() {

        configured(REQUIRED).withPropertyValues("axonia.selfhosted.organization.id=acme-corp-2")
                .run(context -> assertThat(context.getBean(SelfHostedProperties.class).organization().id())
                        .isEqualTo("acme-corp-2"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"login.example.com/tenant/v2.0", "ftp://login.example.com", UNRESOLVABLE,
            "https://${#no-setting-can-be-named-this}/realms/acme"})
    void refusesAnIssuerThatIsNotAnHttpUrl(String issuer) {

        configured(REQUIRED).withPropertyValues("axonia.selfhosted.oidc.issuer-uri=" + issuer)
                .run(context -> assertRejected(context, "issuerUri"));
    }

    @Test
    void refusesAnOrganizationNameLongerThanTheColumn() {

        configured(REQUIRED).withPropertyValues("axonia.selfhosted.organization.name=" + "n".repeat(121))
                .run(context -> assertRejected(context, "organization.name"));
    }

    private static ApplicationContextRunner configured(Map<String, String> settings) {

        return new ApplicationContextRunner().withUserConfiguration(SelfHostedConfiguration.class)
                .withPropertyValues(settings.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                        .toArray(String[]::new));
    }

    private static void assertRejected(AssertableApplicationContext context, String detail) {

        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure()).rootCause().isInstanceOf(BindValidationException.class)
                .hasMessageContaining(detail);
    }
}
