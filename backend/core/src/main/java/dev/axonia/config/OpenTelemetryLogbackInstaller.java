package dev.axonia.config;

import org.springframework.beans.factory.InitializingBean;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

/**
 * Hands the OpenTelemetry SDK to the {@code OTEL} appender that {@code logback-spring.xml} declares.
 *
 * <p>
 * Nothing in Spring Boot does this. Boot's {@code spring-boot-opentelemetry} auto-configures the SDK, the
 * {@code SdkLoggerProvider} and the OTLP log exporter, and touches Logback nowhere at all, so the appender starts with
 * a null {@code openTelemetry} field. In that state it does not fail and it does not warn: it parks each event in a
 * bounded queue of a thousand, then quietly drops everything after. A deployment with all four platform variables set
 * would show traces and metrics arriving and not one log record, off a green build.
 *
 * <p>
 * The install happens in {@code afterPropertiesSet} rather than from an {@code ApplicationRunner} so that the drain of
 * that queue, which is what carries the startup logs, happens as early as a bean can arrange it.
 */
class OpenTelemetryLogbackInstaller implements InitializingBean {

    private final OpenTelemetry openTelemetry;

    OpenTelemetryLogbackInstaller(OpenTelemetry openTelemetry) {

        this.openTelemetry = openTelemetry;
    }

    /**
     * A no-op wherever the appender is not declared, since {@code install} walks the logger context and only touches
     * appenders that are {@code OpenTelemetryAppender} instances. Outside PROD there are none.
     */
    @Override
    public void afterPropertiesSet() {

        OpenTelemetryAppender.install(openTelemetry);
    }
}
