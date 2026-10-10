package dev.axonia.shared.configuration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Where telemetry goes, and the credential it goes with.
 *
 * <p>
 * The mirror this validates is the telemetry block of the production profile, the only profile that sets any of these:
 * {@code AXONIA_TELEMETRY_ENABLED} for the switch and {@code OTEL_EXPORTER_AUTH} for the credential. Renaming a
 * variable on one side and not the other leaves this record validating something nobody exports, so the two are read
 * together, and a test binds this record against that actual file.
 *
 * <p>
 * {@code endpoint} is the OTLP gateway base URL, not a platform variable: all three signals share one gateway host of
 * the telemetry backend, and only the path differs between them. That host names no secret and does not change between
 * deploys, so it is a literal in the profile rather than a third thing an operator has to set. Composing
 * {@code <endpoint>/v1/<signal>} is done in the profile itself, by plain placeholder interpolation against this
 * record's {@code endpoint}.
 *
 * <p>
 * {@code enabled} defaults to false so a developer, the test suite and any future environment export nothing until
 * somebody says otherwise. Turning telemetry on is a deployment decision, never an inherited default. The empty
 * defaults that go with {@code endpoint} and {@code auth} are what let every other profile bind this record at all.
 *
 * <p>
 * {@link #isFullyConfiguredWhenEnabled()} is the guard that actually fires in production, and the shape any optional
 * subsystem's properties take. In PROD, {@code endpoint} is always the literal from the properties file and can never
 * itself be blank, so in practice this guard turns on the one variable that can go wrong there: a platform
 * {@code OTEL_EXPORTER_AUTH} that is unset or set to nothing. Both bind a blank, and blank is legitimate while
 * {@code enabled} is false and a boot-time error while it is true, which no per-field {@code NotBlank} can say. Without
 * it a fat-fingered {@code OTEL_EXPORTER_AUTH=} starts cleanly and every export is refused 401 by the collector, with
 * no local symptom at all. The guard is still written against both fields, rather than narrowed to {@code auth} alone,
 * because nothing about this record stops a future profile from making {@code endpoint} variable again.
 *
 * <p>
 * The {@code Pattern} on every field guards the case those empty defaults do not reach, for the reason
 * {@link EdgeProperties} documents at length: the binder does NOT fail on an unresolved {@code ${...}}, it hands over
 * the placeholder's own literal text. A platform variable whose value contains one would otherwise authenticate with
 * the string {@code ${OTEL_EXPORTER_AUTH}}, and the first symptom would be silence rather than an error.
 */
@Validated
@ConfigurationProperties(prefix = "axonia.telemetry")
record TelemetryProperties(@DefaultValue("false") boolean enabled,
        @DefaultValue("") @Pattern(regexp = RESOLVED, message = UNRESOLVED) String endpoint,
        @DefaultValue("") @Pattern(regexp = RESOLVED, message = UNRESOLVED) String auth) {

    static final String RESOLVED = "(?!.*\\$\\{).*";

    static final String UNRESOLVED = "must be a resolved value, not an unresolved property placeholder";

    @AssertTrue(message = "axonia.telemetry requires endpoint and auth when enabled")
    public boolean isFullyConfiguredWhenEnabled() {

        return !enabled || fullyConfigured();
    }

    /**
     * Whether both values are present, said separately from {@link #isFullyConfiguredWhenEnabled()} because the other
     * combination matters too. Fully configured with {@code enabled} false is a legitimate state - it is how telemetry
     * is switched off during an incident without touching the endpoint - and it is also exactly what setting
     * {@code OTEL_EXPORTER_AUTH} and forgetting {@code AXONIA_TELEMETRY_ENABLED} looks like. Nothing here can tell the
     * two apart, so {@link TelemetryConfig} warns rather than refuses.
     */
    boolean fullyConfigured() {

        return !endpoint.isBlank() && !auth.isBlank();
    }
}
