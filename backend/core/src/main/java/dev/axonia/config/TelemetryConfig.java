package dev.axonia.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * The one place {@link TelemetryProperties} is registered on the application context.
 *
 * <p>
 * Without a registration point the record is never bound, and the {@code Pattern} constraints that are its entire
 * reason for existing never run: a deployment missing {@code OTEL_EXPORTER_AUTH} would start clean and export to
 * nowhere. Registering it here rather than on a feature module keeps it where the rest of this application's telemetry
 * wiring lives, alongside {@link EdgeGateConfig} doing the same for {@link EdgeProperties}.
 *
 * <p>
 * The registration and one WARN, and no bean at all. {@link OpenTelemetryLogbackConfig} explains why the appender
 * install is kept out of here rather than added as a second bean method.
 */
@Configuration
@EnableConfigurationProperties(TelemetryProperties.class)
@Slf4j
class TelemetryConfig {

    /**
     * The one telemetry misconfiguration that cannot be made fatal.
     *
     * <p>
     * {@link TelemetryProperties#isFullyConfiguredWhenEnabled()} refuses to start on every half-set state, but the
     * state where the credential is set and the switch is off is not half-set: it is a healthy deployment that exports
     * nothing. It is what setting {@code OTEL_EXPORTER_AUTH} and forgetting {@code AXONIA_TELEMETRY_ENABLED} produces,
     * and it is also what an operator who has just switched telemetry off during an incident produces, so refusing to
     * boot on it would delete the kill switch to catch a typo. A WARN is right in both readings: one is a mistake being
     * named, the other is a deliberate act being confirmed.
     *
     * <p>
     * Injected on the constructor rather than declared as a bean, so it says its piece once per context and leaves
     * nothing behind.
     */
    TelemetryConfig(TelemetryProperties telemetry) {

        if (!telemetry.enabled() && telemetry.fullyConfigured()) {
            log.warn("Telemetry is fully configured but switched off: OTEL_EXPORTER_AUTH is set and "
                    + "axonia.telemetry.enabled is false, so nothing is exported. Set AXONIA_TELEMETRY_ENABLED=true "
                    + "if that was not intended.");
        }
    }
}
