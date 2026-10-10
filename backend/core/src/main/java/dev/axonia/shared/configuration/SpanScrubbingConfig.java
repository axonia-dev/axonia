package dev.axonia.shared.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.axonia.shared.adapter.out.telemetry.ScrubbingSpanFilter;
import dev.axonia.shared.adapter.out.telemetry.SpanStatusScrubbingProcessor;

/**
 * Wires the two halves of the span scrub: {@link ScrubbingSpanFilter} for attributes and event attributes,
 * {@link SpanStatusScrubbingProcessor} for the status description. Their own javadoc explains why the job needs two
 * seams.
 *
 * <p>
 * A class of its own rather than two more bean methods on {@link TelemetryConfig}, which is kept to the registration of
 * {@link TelemetryProperties} and nothing else. That is not the reason {@link OpenTelemetryLogbackConfig} is separate -
 * its bean mutates the global Logback {@code LoggerContext}, and neither of these two has any side effect at all - it
 * is simply that one telemetry concern per configuration class keeps a test that binds and validates a record from
 * constructing anything besides it.
 *
 * <p>
 * Both beans are deliberately unconditional, unlike the appender install. A scrubber left on while telemetry is off has
 * nothing to protect, because no exporter bean is contributed for a span to reach; a scrubber turned off while
 * telemetry is on, because somebody moved one flag and not the other, is a personal-data leak. Registering them
 * unconditionally removes that failure mode rather than documenting it, and lets {@code TelemetryPersonalDataGuardTest}
 * exercise the real production scrub without turning export on.
 */
@Configuration
class SpanScrubbingConfig {

    @Bean
    ScrubbingSpanFilter scrubbingSpanFilter() {

        return new ScrubbingSpanFilter();
    }

    @Bean
    SpanStatusScrubbingProcessor spanStatusScrubbingProcessor() {

        return new SpanStatusScrubbingProcessor();
    }
}
