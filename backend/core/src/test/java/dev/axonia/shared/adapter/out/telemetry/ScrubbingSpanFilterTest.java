package dev.axonia.shared.adapter.out.telemetry;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.micrometer.tracing.otel.bridge.CompositeSpanExporter;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts what an exporter receives, never what the filter returns, because the only claim worth making is about what
 * leaves the process. The wiring is the production one in miniature: Boot's
 * {@code OpenTelemetryTracingAutoConfiguration} builds exactly this {@link CompositeSpanExporter} around every
 * {@code SpanFilter} bean and every {@code SpanExporter} bean, so an {@link InMemorySpanExporter} placed inside it sits
 * where the OTLP exporter sits in PROD.
 *
 * <p>
 * That composite is also what proves the ordering, without any statement about bean order: it maps a span through its
 * filters and only then calls {@code export} on the exporters it holds, so an attribute an exporter never sees is one
 * scrubbing removed first.
 */
class ScrubbingSpanFilterTest {

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();

    private final SdkTracerProvider provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(
            new CompositeSpanExporter(List.of(exporter), List.of(), List.of(), List.of(new ScrubbingSpanFilter()))))
            .build();

    @Test
    void keepsAnAllowlistedAttribute() {

        // given
        provider.get("test").spanBuilder("s").setAttribute(AttributeKey.stringKey("organization_id"), "acme")
                .startSpan().end();

        // when
        var actual = exported(AttributeKey.stringKey("organization_id"));

        // then
        assertThat(actual).isEqualTo("acme");
    }

    @Test
    void dropsAnAttributeThatIsNotOnTheList() {

        // given
        provider.get("test").spanBuilder("s")
                .setAttribute(AttributeKey.stringKey("enduser.email"), "someone@example.com").startSpan().end();

        // when
        var actual = exported(AttributeKey.stringKey("enduser.email"));

        // then
        assertThat(actual).isNull();
    }

    @Test
    void dropsTheFullUrlBecauseAPathCanCarryAHandle() {

        // given
        provider.get("test").spanBuilder("s")
                .setAttribute(AttributeKey.stringKey("url.full"), "https://axonia.example/m/jane-doe").startSpan()
                .end();

        // when
        var actual = exported(AttributeKey.stringKey("url.full"));

        // then
        assertThat(actual).isNull();
    }

    /**
     * A long stays a long. Rewriting through {@code setTags} rather than {@code setTypedTags} would export
     * {@code http.response.status_code} as the string "500", which no backend numeric filter matches, and no assertion
     * on presence alone would notice.
     */
    @Test
    void keepsTheTypeOfAnAllowlistedAttribute() {

        // given
        provider.get("test").spanBuilder("s").setAttribute(AttributeKey.longKey("http.response.status_code"), 500L)
                .startSpan().end();

        // when
        var actual = exported(AttributeKey.longKey("http.response.status_code"));

        // then
        assertThat(actual).isEqualTo(500L);
    }

    /**
     * The event is what {@code span.error(throwable)} produces for a failed request, and its {@code exception.message}
     * is the likeliest personal-data carrier on the whole span: a database constraint violation quotes the offending
     * column values back verbatim.
     */
    @Test
    void dropsEveryEventAttributeAndKeepsTheEventName() {

        // given
        var span = provider.get("test").spanBuilder("s").startSpan();
        span.recordException(new IllegalStateException("Key (email)=(someone@example.com) already exists"));
        span.end();

        // when
        var actual = firstExportedSpan().getEvents();

        // then
        assertThat(actual).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("exception");
            assertThat(event.getAttributes()).isEqualTo(Attributes.empty());
        });
    }

    /** A span with no events at all must survive the rewrite unchanged rather than acquiring one. */
    @Test
    void leavesASpanWithNoEventsAlone() {

        // given
        provider.get("test").spanBuilder("s").startSpan().end();

        // when
        var actual = firstExportedSpan().getEvents();

        // then
        assertThat(actual).isEmpty();
    }

    private <T> T exported(AttributeKey<T> key) {

        return firstExportedSpan().getAttributes().get(key);
    }

    private SpanData firstExportedSpan() {

        return exporter.getFinishedSpanItems().getFirst();
    }
}
