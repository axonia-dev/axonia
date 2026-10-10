package dev.axonia.identity.configuration;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntraGraphPropertiesTest {

    @Test
    void binding_rejectsAnUnresolvedPlaceholder() {

        // given - Spring binds the literal text of an unresolved ${VAR}, it does not fail
        var validator = Validation.buildDefaultValidatorFactory().getValidator();

        // when
        var violations = validator.validate(new EntraGraphProperties("${AXONIA_ENTRA_CLIENT_ID}", "secret"));

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    void binding_acceptsNullBecauseLocalAndTestContextsConfigureNoGraphApp() {

        // given
        var validator = Validation.buildDefaultValidatorFactory().getValidator();

        // when / then
        assertThat(validator.validate(new EntraGraphProperties(null, null))).isEmpty();
    }

    @Test
    void isConfigured_bothClientIdAndSecretNonBlank_returnsTrue() {

        assertThat(new EntraGraphProperties("app-client-id", "app-secret").isConfigured()).isTrue();
    }

    @Test
    void isConfigured_clientIdNull_returnsFalse() {

        assertThat(new EntraGraphProperties(null, "app-secret").isConfigured()).isFalse();
    }

    @Test
    void isConfigured_clientIdBlank_returnsFalse() {

        assertThat(new EntraGraphProperties("  ", "app-secret").isConfigured()).isFalse();
    }

    @Test
    void isConfigured_clientSecretNull_returnsFalse() {

        assertThat(new EntraGraphProperties("app-client-id", null).isConfigured()).isFalse();
    }

    @Test
    void isConfigured_clientSecretBlank_returnsFalse() {

        assertThat(new EntraGraphProperties("app-client-id", "").isConfigured()).isFalse();
    }
}
