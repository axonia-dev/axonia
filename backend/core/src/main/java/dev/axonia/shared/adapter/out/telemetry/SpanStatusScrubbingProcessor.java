package dev.axonia.shared.adapter.out.telemetry;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.internal.ExtendedSpanProcessor;

/**
 * Blanks the description of an ERROR span status, which is the one carrier of personal data {@link ScrubbingSpanFilter}
 * cannot reach.
 *
 * <p>
 * Two seams exist rather than one because no single one of the SDK's own mutation windows admits both jobs.
 * {@code OtelSpan.error} does two things for a failed request: it records an exception event, and it calls
 * {@code setStatus(ERROR, throwable.getMessage())}, putting the raw message in the status description. A
 * {@code DuplicateKeyException} off {@code identity_user} carries Postgres' own {@code Key (organization_id,
 * email)=(...)} text in that message, so the description is a customer's address leaving the process. The filter cannot
 * touch it: {@code FinishedSpan} has no status accessor at all, {@code setError} appends an event rather than writing
 * the status, and {@code OtelFinishedSpan.MutableSpanData} does not override {@code getStatus()}, so the exported
 * status is whatever the original {@code SpanData} held. Conversely this class cannot do the filter's job, because no
 * span API removes an attribute. Each seam does the half only it can. A wrapping {@code SpanExporter} rebuilding
 * {@code SpanData} would reach both halves at once, and was rejected on reach rather than on timing, for the reason
 * {@link ScrubbingSpanFilter} records. Timing is where the two shipped seams genuinely differ, and it favours this one:
 * {@code onEnding} runs before any other processor's {@code onEnd}, while the filter runs at export time, after them
 * all.
 *
 * <p>
 * {@code onEnding} is the last window in which the description both exists and is still writable.
 * {@code SdkSpan.endInternal} calls it while the span is in state {@code ENDING} on the ending thread, which is exactly
 * what {@code isModifiableByCurrentThread()} admits, and only then sets {@code ENDED} and calls {@code onEnd}. So a
 * write here lands where the same write in {@code onEnd} is refused, and {@code onStart} is no use because the status
 * is not set until the request has failed. It runs before every exporter for the same reason: the whole of
 * {@code onEnd}, {@code BatchSpanProcessor}'s queue and the export all come after it.
 *
 * <p>
 * {@code ExtendedSpanProcessor} lives in an {@code internal} package and its javadoc says the API may change. That is
 * accepted knowingly: a removal or rename is a compile error, which is a loud failure rather than a silent one, and a
 * change in when {@code onEnding} fires is what {@code SpanStatusScrubbingProcessorTest} asserts against a real
 * exporter.
 */
public class SpanStatusScrubbingProcessor implements ExtendedSpanProcessor {

    /**
     * Only an ERROR status is touched. Rewriting any other would invent a failure: the SDK ignores a description on a
     * non-ERROR status anyway, so a description can only exist where the code is already ERROR, and issuing
     * {@code setStatus(ERROR, "")} unconditionally would mark every healthy span as failed.
     */
    @Override
    public void onEnding(ReadWriteSpan span) {

        if (span.toSpanData().getStatus().getStatusCode() == StatusCode.ERROR) {
            span.setStatus(StatusCode.ERROR, "");
        }
    }

    @Override
    public boolean isOnEndingRequired() {

        return true;
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {

        // Nothing to do: a status is set during the span's life, and the ending window is where it can still be
        // rewritten.
    }

    @Override
    public boolean isStartRequired() {

        return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {

        // Nothing to do, and nothing that could be done: the span is ENDED here and refuses every write.
    }

    @Override
    public boolean isEndRequired() {

        return false;
    }
}
