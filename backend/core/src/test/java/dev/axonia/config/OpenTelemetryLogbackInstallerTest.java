package dev.axonia.config;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import ch.qos.logback.classic.Logger;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot auto-configures the OpenTelemetry SDK and never tells Logback about it, so this install is the whole reason PROD
 * log records leave the process. It is also the failure that hides best: an appender with no SDK neither throws nor
 * warns, it buffers a thousand events and drops the rest, so the only way to hold the wiring is to put a real appender
 * in front of a real SDK and watch a record come out the far end.
 */
class OpenTelemetryLogbackInstallerTest {

    private static final String MESSAGE = "a record that has to reach the collector";

    private final InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();

    private final OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setLoggerProvider(
            SdkLoggerProvider.builder().addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter)).build())
            .build();

    @Test
    void installingHandsTheSdkToTheAppenderSoRecordsReachTheExporter() {

        withAttachedAppender(logger -> {
            new OpenTelemetryLogbackInstaller(sdk).afterPropertiesSet();
            logger.info(MESSAGE);
        });

        assertThat(exporter.getFinishedLogRecordItems()).singleElement()
                .satisfies(record -> assertThat(record.getBodyValue().asString()).isEqualTo(MESSAGE));
    }

    /**
     * The other half of the pair, and the one that makes the first assertion mean something: skip the install and the
     * appender swallows the record without complaining, which is precisely how this shipped unnoticed.
     */
    @Test
    void withoutTheInstallTheAppenderSwallowsEveryRecord() {

        withAttachedAppender(logger -> logger.info(MESSAGE));

        assertThat(exporter.getFinishedLogRecordItems()).isEmpty();
    }

    @Test
    void theInstallerIsRegisteredExactlyWhenTelemetryIsEnabled() {

        var runner = new ApplicationContextRunner().withUserConfiguration(OpenTelemetryLogbackConfig.class)
                .withBean(OpenTelemetry.class, OpenTelemetry::noop);

        runner.withPropertyValues("axonia.telemetry.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(OpenTelemetryLogbackInstaller.class));

        runner.withPropertyValues("axonia.telemetry.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(OpenTelemetryLogbackInstaller.class));
    }

    /**
     * Attaches a real appender to a logger of its own, so the assertions run against Logback rather than a stand-in,
     * and detaches it again: {@code install} walks the one global logger context, and a leftover appender would make
     * every later test in this JVM a participant.
     */
    private static void withAttachedAppender(java.util.function.Consumer<Logger> body) {

        var logger = (Logger) LoggerFactory.getLogger(OpenTelemetryLogbackInstallerTest.class.getName() + ".subject");
        var appender = new OpenTelemetryAppender();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        try {
            body.accept(logger);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
