package dev.axonia.config;

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts at an exporter, like {@link ScrubbingSpanFilterTest}, and for the stronger of the two reasons: this seam
 * depends on {@code onEnding} being called while the span is still writable, which is an {@code internal} SDK contract
 * that could change without a compile error. A test that inspected the processor rather than the export would keep
 * passing through exactly that change.
 */
class SpanStatusScrubbingProcessorTest {

    private static final String SENTINEL = "Key (email)=(someone@example.com) already exists";

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();

    private final SdkTracerProvider provider = SdkTracerProvider.builder()
            .addSpanProcessor(new SpanStatusScrubbingProcessor()).addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();

    @Test
    void blanksTheDescriptionOfAnErrorStatus() {

        // given
        var span = provider.get("test").spanBuilder("s").startSpan();
        span.setStatus(StatusCode.ERROR, SENTINEL);
        span.end();

        // when
        var actual = exportedStatus();

        // then
        assertThat(actual.getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(actual.getDescription()).isEmpty();
    }

    /** The error itself is not what leaks; deleting it would cost every failed request its severity in Grafana. */
    @Test
    void leavesAHealthySpanUnset() {

        // given
        provider.get("test").spanBuilder("s").startSpan().end();

        // when
        var actual = exportedStatus();

        // then
        assertThat(actual).isEqualTo(StatusData.unset());
    }

    private StatusData exportedStatus() {

        return exporter.getFinishedSpanItems().getFirst().getStatus();
    }
}
