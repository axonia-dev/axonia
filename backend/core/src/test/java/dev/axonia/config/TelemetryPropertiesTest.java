package dev.axonia.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TelemetryConfig} is the configuration under test rather than a local holder: it is the only thing registering
 * {@link TelemetryProperties} on the real context, and a record that is never registered is a record whose constraints
 * never run.
 */
class TelemetryPropertiesTest {

    /**
     * Deliberately not the real variable name. The constraint under test is "any unresolved placeholder", so naming
     * {@code OTEL_EXPORTER_AUTH} would make the assertion depend on that variable being absent from whatever
     * environment runs the suite. A name no environment variable and no system property can carry is unresolvable by
     * construction rather than by convention, exactly as {@link EdgeGateWiringTest} does.
     */
    private static final String UNRESOLVABLE = "${no environment variable can be named this}";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TelemetryConfig.class);

    private final ApplicationContextRunner enabled = runner.withPropertyValues("axonia.telemetry.enabled=true",
            "axonia.telemetry.endpoint=otlp.example", "axonia.telemetry.auth=Basic abc");

    @Test
    void bindsAResolvedConfiguration() {

        enabled.run(context -> {
            var telemetry = context.getBean(TelemetryProperties.class);
            assertThat(telemetry.enabled()).isTrue();
            assertThat(telemetry.endpoint()).isEqualTo("otlp.example");
            assertThat(telemetry.auth()).isEqualTo("Basic abc");
        });
    }

    /**
     * The binder does not fail on an unresolved placeholder, it binds the placeholder's own text. Both fields are
     * covered rather than only the credential, because an endpoint left as its own variable name is the failure that
     * looks like success: the application starts, and telemetry silently goes nowhere.
     */
    @ParameterizedTest
    @CsvSource({"endpoint,endpoint", "auth,auth"})
    void refusesAnUnresolvedPlaceholder(String property, String field) {

        enabled.withPropertyValues("axonia.telemetry." + property + "=" + UNRESOLVABLE)
                .run(context -> assertValidationRejected(context, field, TelemetryProperties.UNRESOLVED));
    }

    /**
     * The second way a platform variable goes wrong, and the one no {@code Pattern} can see: the variable exists and is
     * empty, so the binder hands over a blank rather than a placeholder. Left accepted, a fat-fingered
     * {@code OTEL_EXPORTER_AUTH=} starts the application and every export is refused by the collector, which is a
     * failure with no local symptom whatsoever. Matches
     * {@code EdgeGateWiringTest.refusesToStartWhenTheSecretVariableIsSetButEmpty}.
     */
    @ParameterizedTest
    @ValueSource(strings = {"endpoint", "auth"})
    void refusesAValueThatIsSetButEmptyWhileEnabled(String property) {

        enabled.withPropertyValues("axonia.telemetry." + property + "=")
                .run(context -> assertValidationRejected(context, "fullyConfiguredWhenEnabled",
                        "axonia.telemetry requires endpoint and auth when enabled"));
    }

    /**
     * The shape every environment but PROD boots in: nothing configured at all, and nothing rejected for it.
     */
    @Test
    void allowsEverythingBlankWhenDisabled() {

        runner.withPropertyValues("axonia.telemetry.enabled=false").run(context -> {
            var telemetry = context.getBean(TelemetryProperties.class);
            assertThat(telemetry.enabled()).isFalse();
            assertThat(telemetry.endpoint()).isEmpty();
            assertThat(telemetry.auth()).isEmpty();
        });
    }

    /**
     * The one misconfiguration nothing can refuse: setting the two platform values and forgetting the switch produces a
     * healthy deployment that exports nothing, and it is indistinguishable from an operator having deliberately killed
     * telemetry during an incident. The message has to be there for the first reading and has to be a WARN rather than
     * a failure for the second.
     */
    @Test
    void warnsWhenEverythingIsConfiguredAndTheSwitchIsOff() {

        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        var logger = (Logger) LoggerFactory.getLogger(TelemetryConfig.class);
        logger.addAppender(logs);
        try {
            enabled.withPropertyValues("axonia.telemetry.enabled=false").run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(logs.list).singleElement()
                        .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
                assertThat(logs.list.getFirst().getFormattedMessage()).contains("AXONIA_TELEMETRY_ENABLED");
            });
        } finally {
            logger.detachAppender(logs);
        }
    }

    /** Nothing configured is the ordinary state of every environment but PROD, and it is not worth a line. */
    @Test
    void saysNothingWhenNothingIsConfigured() {

        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        var logger = (Logger) LoggerFactory.getLogger(TelemetryConfig.class);
        logger.addAppender(logs);
        try {
            runner.withPropertyValues("axonia.telemetry.enabled=false").run(context -> assertThat(logs.list).isEmpty());
        } finally {
            logger.detachAppender(logs);
        }
    }

    private static void assertValidationRejected(AssertableApplicationContext context, String field, String message) {

        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure()).rootCause().isInstanceOf(BindValidationException.class)
                .hasMessageContaining(field).hasMessageContaining(message);
    }
}
