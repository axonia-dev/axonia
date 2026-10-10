package dev.axonia.shared.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.opentelemetry.api.OpenTelemetry;

/**
 * Wires {@link OpenTelemetryLogbackInstaller}, and exists as a class of its own for one reason: creating this bean has
 * a process-wide side effect.
 *
 * <p>
 * {@code OpenTelemetryAppender.install} mutates the single global Logback {@code LoggerContext}, so every application
 * context that builds the installer changes state shared by the whole JVM. Sitting on {@link TelemetryConfig}, it made
 * {@code TelemetryPropertiesTest} - a test that binds and validates a record and nothing else - silently install a
 * no-op SDK across the JVM on each of its {@code enabled=true} cases. Nothing breaks today, because the suite is
 * sequential and the one other test attaching an appender detaches it in a {@code finally}, but it is invisible
 * coupling of exactly the kind that turns into an order-dependent failure once a suite gains parallelism. Please do not
 * helpfully merge the two classes back together.
 */
@Configuration
class OpenTelemetryLogbackConfig {

    /**
     * Conditional on the same flag the exporters are, rather than on the PROD profile the appender is declared under.
     * Where the appender is absent the installer is harmless - {@code install} walks the logger context, finds no
     * {@code OpenTelemetryAppender} and returns - which is what makes the coarser of the two conditions the safe one to
     * use.
     *
     * <p>
     * {@link OpenTelemetry} is a hard dependency, deliberately. Boot's {@code OpenTelemetrySdkAutoConfiguration} always
     * contributes one unless {@code management.opentelemetry.enabled} is turned off, and a context that has telemetry
     * enabled with no SDK in it is a misconfiguration worth failing on rather than logging into a void.
     */
    @Bean
    @ConditionalOnProperty(prefix = "axonia.telemetry", name = "enabled", havingValue = "true")
    OpenTelemetryLogbackInstaller openTelemetryLogbackInstaller(OpenTelemetry openTelemetry) {

        return new OpenTelemetryLogbackInstaller(openTelemetry);
    }
}
