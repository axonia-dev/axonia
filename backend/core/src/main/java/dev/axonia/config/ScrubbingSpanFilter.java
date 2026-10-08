package dev.axonia.config;

import java.util.Map;
import java.util.stream.Collectors;

import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.exporter.SpanFilter;

/**
 * Removes every span attribute that {@link AttributeAllowlist} does not name, and every attribute of every span event,
 * after the span has ended and before any exporter sees it.
 *
 * <p>
 * A {@link SpanFilter} rather than a {@code SpanProcessor} mutating in {@code onEnd}, because that is a silent no-op:
 * {@code SdkSpan.endInternal} sets {@code hasEnded = ENDED} before calling {@code onEnd}, and every mutator on
 * {@code SdkSpan} is guarded by {@code isModifiableByCurrentThread()}, so every write such a processor attempts is
 * refused and logged at FINE - a leak that reads exactly like a fix. Boot's
 * {@code OpenTelemetryTracingAutoConfiguration} builds its {@code BatchSpanProcessor} around a
 * {@code CompositeSpanExporter} holding every {@code SpanFilter} bean, and that composite maps each span through the
 * filters before it hands anything to any exporter, which is what makes this hook both effective and free of any
 * ordering argument between beans.
 *
 * <p>
 * No <em>span</em> API can drop an attribute at all: {@code io.opentelemetry.api.trace.Span} declares setters and
 * nothing else, and {@code setAttribute(key, null)} returns before it reaches the attribute map. Rewriting the whole
 * map is the only way, and Micrometer's {@code FinishedSpan} - not any SDK type - is what offers it.
 *
 * <p>
 * A wrapping {@code SpanExporter} could rebuild {@code SpanData} and do both halves of the scrub in one place. It was
 * rejected on reach, not on timing: a {@code SpanFilter} bean is applied by the composite to every {@code SpanExporter}
 * bean in the context automatically, whereas a wrapping exporter covers only the exporters something remembered to wrap
 * and would miss any registered directly - the guard's own {@code InMemorySpanExporter} among them. That rejection is
 * why {@link SpanStatusScrubbingProcessor} has to exist rather than this class doing the whole job.
 *
 * <p>
 * Timing is emphatically <em>not</em> a reason to prefer this seam, and nothing here should be read as saying so. This
 * filter runs at export time, after every {@code SpanProcessor.onEnd} and after batching - the same position a wrapping
 * exporter would occupy. A second processor registered with its own exporter therefore receives the span with its
 * attributes intact, and the only thing standing between that and a leak is
 * {@code TelemetryPersonalDataGuardTest.nothingElseRegistersASpanProcessor}.
 *
 * <p>
 * The attribute rewrite goes through the typed map rather than {@code setTags}, which coerces every value to its string
 * form: an allowlisted long or boolean would survive the scrub with its type quietly changed, and a backend querying it
 * as a number would match nothing.
 *
 * <p>
 * Events are scrubbed wholesale rather than against the allowlist, because they carry the worst of it and because
 * {@code FinishedSpan} offers no finer instrument. {@code TracingObservationHandler.tagSpan} calls
 * {@code span.error(throwable)} for a request that failed, and {@code OtelSpan.error} turns that into
 * {@code recordException}, whose event carries {@code exception.message} and {@code exception.stacktrace}. A
 * {@code DuplicateKeyException} off {@code identity_user} embeds Postgres' own {@code Key (organization_id,
 * email)=(...)} text in that message, so the event is a direct route for a customer's address to leave the process.
 * {@code OtelFinishedSpan.setEvents} rebuilds each event as name plus timestamp with {@code Attributes.empty()}, so
 * feeding it the events it just returned keeps every event name and drops every event attribute. The exception's type
 * is not lost with them: Micrometer already publishes it as the {@code exception} tag, which is allowlisted.
 */
class ScrubbingSpanFilter implements SpanFilter {

    @Override
    public FinishedSpan map(FinishedSpan span) {

        return span.setTypedTags(permitted(span.getTypedTags())).setEvents(span.getEvents());
    }

    private static Map<String, Object> permitted(Map<String, Object> tags) {

        return tags.entrySet().stream().filter(tag -> AttributeAllowlist.PERMITTED.contains(tag.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
