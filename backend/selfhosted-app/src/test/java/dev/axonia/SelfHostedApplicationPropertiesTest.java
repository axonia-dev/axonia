package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Self-hosted edition's production configuration, read as the files it ships, because every test context overrides
 * the lines that matter most here: the secrets.
 */
class SelfHostedApplicationPropertiesTest {

    private static final Properties APPLICATION = load("application.properties");

    private static final Properties SETTINGS = load("axonia-selfhosted.properties");

    /**
     * An imported file outranks the file importing it, so a Self-hosted setting written here would lose to the same key
     * in {@code axonia-core.properties}; the settings live in the file imported after core's.
     */
    @Test
    void applicationPropertiesHoldsTheImportAndTheChainAlone() {

        assertThat(APPLICATION.stringPropertyNames()).containsExactlyInAnyOrder("spring.config.import",
                "spring.flyway.locations");
        assertThat(APPLICATION.getProperty("spring.config.import"))
                .isEqualTo("classpath:axonia-core.properties,classpath:axonia-selfhosted.properties");
    }

    @Test
    void takesEverySecretAndAddressFromAVariableWithNoDefault() {

        assertThat(SETTINGS).containsAllEntriesOf(Map.of("spring.flyway.url", "${DATABASE_URL}", "spring.flyway.user",
                "${DATABASE_OWNER_USERNAME}", "spring.flyway.password", "${DATABASE_OWNER_PASSWORD}",
                "spring.flyway.placeholders.app-password", "${DATABASE_PASSWORD}",
                "spring.flyway.placeholders.communication-read-password", "${DATABASE_COMMUNICATION_READ_PASSWORD}",
                "axonia.public-base-url", "${AXONIA_PUBLIC_BASE_URL}", "axonia.edge.shared-secrets",
                "${AXONIA_EDGE_SHARED_SECRETS}", "axonia.communication.unsubscribe-secret",
                "${AXONIA_UNSUBSCRIBE_SECRET}"));
    }

    @Test
    void takesTheSettingsKeyFromAVariableWithNoDefaultAndPreviousKeysFromAnOptionalOne() {

        assertThat(SETTINGS.getProperty("axonia.installation.settings-key")).isEqualTo("${AXONIA_SETTINGS_KEY}");
        assertThat(SETTINGS.getProperty("axonia.installation.previous-settings-keys"))
                .isEqualTo("${AXONIA_SETTINGS_PREVIOUS_KEYS:}");
    }

    @Test
    void leavesSpringBootsMailSenderAndItsHealthCheckOut() {

        assertThat(SETTINGS.getProperty("spring.autoconfigure.exclude").split(",")).containsExactlyInAnyOrder(
                "org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration",
                "org.springframework.boot.mail.autoconfigure.MailSenderValidatorAutoConfiguration",
                "org.springframework.boot.mail.autoconfigure.MailHealthContributorAutoConfiguration");
    }

    @Test
    void gatesEveryRequestThroughTheEdge() {

        assertThat(SETTINGS.getProperty("axonia.edge.gate-enabled")).isEqualTo("true");
    }

    @Test
    void leavesTheTokenDecoderToTheSelfHostedSettings() {

        assertThat(SETTINGS.stringPropertyNames())
                .noneMatch(name -> name.startsWith("spring.security.oauth2.resourceserver."));
    }

    @Test
    void keepsRateLimitsInMemoryAndLeavesRedisOutOfHealth() {

        assertThat(SETTINGS.getProperty("axonia.rate-limit.store")).isEqualTo("memory");
        assertThat(SETTINGS.getProperty("management.health.redis.enabled")).isEqualTo("false");
    }

    @Test
    void exposesHealthAloneAndNoDetails() {

        assertThat(SETTINGS.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(SETTINGS.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
    }

    @Test
    void storesMediaLocallyAndNamesNoStorageKey() {

        assertThat(SETTINGS.getProperty("app.media.type")).isEqualTo("local");
        assertThat(SETTINGS.stringPropertyNames()).noneMatch(name -> name.startsWith("app.media.s3."));
    }

    @Test
    void namesNoSelfHostedSettingAndNoDefaultForOne() {

        assertThat(SETTINGS.stringPropertyNames()).noneMatch(name -> name.startsWith("axonia.selfhosted."));
    }

    private static Properties load(String file) {

        var properties = new Properties();
        try (var in = Files.newInputStream(Path.of("src/main/resources", file))) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }
}
