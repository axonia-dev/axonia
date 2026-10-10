package dev.axonia.installation.configuration;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import dev.axonia.installation.domain.SettingsKeys;
import dev.axonia.installation.domain.TestKeys;

import static org.assertj.core.api.Assertions.assertThat;

class InstallationConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(InstallationConfiguration.class);

    @Test
    void readsTheSettingsKeys() {

        runner.withPropertyValues("axonia.installation.settings-key=" + TestKeys.CURRENT,
                "axonia.installation.previous-settings-keys=" + TestKeys.PREVIOUS).run(context -> {
                    var keys = context.getBean(SettingsKeys.class);
                    assertThat(keys.current().id()).isEqualTo(TestKeys.CURRENT_ID);
                    assertThat(keys.byId(TestKeys.PREVIOUS_ID)).isPresent();
                    assertThat(context.getBean(InstallationProperties.class))
                            .hasToString("InstallationProperties[settingsKey=<hidden>, previousSettingsKeys=<hidden>]");
                });
    }

    @Test
    void refusesToStartWithAMalformedKeyAndNeverPrintsIt() {

        runner.withPropertyValues("axonia.installation.settings-key=bm90LTMyLWJ5dGVz").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().isInstanceOf(IllegalStateException.class).hasMessage(
                    "AXONIA_SETTINGS_KEY must be 32 random bytes in base64, as openssl rand -base64 32 prints them.");
            assertThat(context.getStartupFailure()).hasStackTraceContaining("AXONIA_SETTINGS_KEY")
                    .satisfies(failure -> assertThat(stackTraceOf(failure)).doesNotContain("bm90LTMyLWJ5dGVz"));
        });
    }

    @Test
    void refusesToStartWithAMalformedOverlayVariable() {

        runner.withPropertyValues("axonia.installation.settings-key=" + TestKeys.CURRENT, "axonia.mail.port=smtp")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().isInstanceOf(IllegalStateException.class)
                            .hasMessage("AXONIA_MAIL_PORT must be a port number from 1 to 65535.");
                    assertThat(stackTraceOf(context.getStartupFailure())).doesNotContain("smtp");
                });
    }

    /** A host delivery would refuse, with an ideographic space, refuses the start and names its variable. */
    @Test
    void refusesToStartWithAHostDeliveryWouldRefuse() {

        runner.withPropertyValues("axonia.installation.settings-key=" + TestKeys.CURRENT,
                "axonia.mail.host=smtp\u3000acme.test").run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().isInstanceOf(IllegalStateException.class)
                            .hasMessage("AXONIA_MAIL_HOST must be a host name with no space or control character.");
                });
    }

    private static String stackTraceOf(Throwable failure) {

        var text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }
}
