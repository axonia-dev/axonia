package dev.axonia.shared.adapter.in.web;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;

import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The filter's own behaviour, driven directly. {@link RequestTelemetryInstalledTest} is the companion claim that Spring
 * actually installs it in the chain a request travels, and that it sits behind authentication there.
 */
class RequestTelemetryFilterTest {

    private static final String ORGANIZATION = "acme";

    private static final String USER = "9f3c1d20-0b8a-4f66-9a1e-2d5f0c7a4b11";

    private static final String HANDLE_BEARING_URI = "/axonia/members/ada.lovelace";

    private static final String ROUTE = "/axonia/members/{handle}";

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(RequestTelemetryFilter.class);

    /** DEBUG rather than the inherited INFO, so the client-disconnect case has something to assert on. */
    @BeforeEach
    void captureLogs() {

        logs.start();
        filterLogger.addAppender(logs);
        filterLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void releaseLogsAndMdc() {

        filterLogger.setLevel(null);
        filterLogger.detachAppender(logs);
        logs.stop();
        MDC.clear();
    }

    @Test
    void putsTheTenantInTheMdcForTheDurationOfTheRequest() throws Exception {

        var seen = new AtomicReference<List<String>>();
        FilterChain chain = (request, response) -> seen.set(List.of(MDC.get("organization_id"), MDC.get("user_id")));

        filterFor(ORGANIZATION, USER).doFilter(request(), new MockHttpServletResponse(), chain);

        assertThat(seen.get()).containsExactly(ORGANIZATION, USER);
    }

    @Test
    void clearsTheMdcAfterwardsSoAPooledThreadCarriesNothingIntoTheNextRequest() throws Exception {

        filterFor(ORGANIZATION, USER).doFilter(request(), new MockHttpServletResponse(), (rq, rs) -> {
        });

        assertThat(MDC.get("organization_id")).isNull();
        assertThat(MDC.get("user_id")).isNull();
    }

    @Test
    void clearsTheMdcEvenWhenTheChainThrows() {

        assertThatThrownBy(
                () -> filterFor(ORGANIZATION, USER).doFilter(request(), new MockHttpServletResponse(), boom()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get("organization_id")).isNull();
        assertThat(MDC.get("user_id")).isNull();
    }

    /**
     * The resolvers are injected seams, and one of them throwing between the two stamps is what would leave a tenant
     * behind on a pooled container thread. Not reachable with today's two lambdas; entirely reachable with a resolver
     * built on {@code CurrentToken.organization()}, which throws when the token carries no org claim.
     */
    @Test
    void clearsTheMdcWhenAResolverItselfThrows() {

        var filter = new RequestTelemetryFilter(() -> Optional.of(ORGANIZATION), () -> {
            throw new IllegalStateException("no subject");
        });

        assertThatThrownBy(() -> filter.doFilter(request(), new MockHttpServletResponse(), (rq, rs) -> {
        })).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get("organization_id")).isNull();
        assertThat(MDC.get("user_id")).isNull();
    }

    @Test
    void stampsNothingWhenNobodyIsAuthenticated() throws Exception {

        var seen = new AtomicReference<String>();
        FilterChain chain = (request, response) -> seen.set(MDC.get("organization_id"));

        new RequestTelemetryFilter(Optional::empty, Optional::empty).doFilter(request(), new MockHttpServletResponse(),
                chain);

        assertThat(seen.get()).isNull();
    }

    /**
     * A blank identifier is worse than none: it is a key present on every signal carrying nothing, so a query grouping
     * by tenant silently gains an empty bucket.
     */
    @Test
    void stampsNothingForABlankIdentifier() throws Exception {

        var seen = new AtomicReference<String>();
        FilterChain chain = (request, response) -> seen.set(MDC.get("organization_id"));

        filterFor("   ", USER).doFilter(request(), new MockHttpServletResponse(), chain);

        assertThat(seen.get()).isNull();
    }

    /**
     * The span carries the same two identifiers as the MDC, which is what lets a log line and its trace be joined on
     * tenant rather than on trace id alone.
     */
    @Test
    void putsTheTenantOnTheServerSpanAsWell() throws Exception {

        var exporter = InMemorySpanExporter.create();
        try (var tracing = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()) {
            var span = tracing.get("test").spanBuilder("GET " + ROUTE).startSpan();
            try (var scope = span.makeCurrent()) {
                filterFor(ORGANIZATION, USER).doFilter(request(), new MockHttpServletResponse(), (rq, rs) -> {
                });
            } finally {
                span.end();
            }

            // Inside the provider's scope on purpose: shutting it down clears the exporter's collected spans.
            var attributes = exporter.getFinishedSpanItems().getFirst().getAttributes();
            assertThat(attributes.get(AttributeKey.stringKey("organization_id"))).isEqualTo(ORGANIZATION);
            assertThat(attributes.get(AttributeKey.stringKey("user_id"))).isEqualTo(USER);
        }
    }

    @Test
    void rethrowsWhatItLogsSoEveryStatusCodeStaysAsItWas() {

        assertThatThrownBy(
                () -> filterFor(ORGANIZATION, USER).doFilter(request(), new MockHttpServletResponse(), boom()))
                .isInstanceOf(IllegalStateException.class).hasMessage("boom");

        assertThat(logs.list).singleElement()
                .satisfies(event -> assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom"));
    }

    /** The chain declares checked exceptions too, and an escaping {@link IOException} is the same incident. */
    @Test
    void logsAndRethrowsACheckedFailureFromTheChain() {

        FilterChain broken = (request, response) -> {
            throw new IOException("connection reset");
        };

        assertThatThrownBy(
                () -> filterFor(ORGANIZATION, USER).doFilter(request(), new MockHttpServletResponse(), broken))
                .isInstanceOf(IOException.class);

        assertThat(logs.list).hasSize(1);
    }

    /**
     * Paths in this product carry member handles, and a log record shipped to a third-party backend leaks one exactly
     * as a span attribute would. The templated route says everything an incident needs and nothing a handle would add.
     */
    @Test
    void logsTheTemplatedRouteAndNeverTheRawUri() {

        var request = request();
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, ROUTE);

        assertThatThrownBy(() -> filterFor(ORGANIZATION, USER).doFilter(request, new MockHttpServletResponse(), boom()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).isEqualTo("Unhandled failure serving GET " + ROUTE);
            assertThat(event.getFormattedMessage()).doesNotContain("ada.lovelace");
        });
    }

    /**
     * The dispatcher only publishes the pattern once it has matched a handler, so a failure before matching has none.
     * The method alone is logged then, because the only other thing to hand is the raw URI.
     */
    @Test
    void logsTheMethodAloneWhenNoHandlerWasEverMatched() {

        assertThatThrownBy(
                () -> filterFor(ORGANIZATION, USER).doFilter(request(), new MockHttpServletResponse(), boom()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).isEqualTo("Unhandled failure serving GET");
            assertThat(event.getFormattedMessage()).doesNotContain("ada.lovelace");
        });
    }

    /**
     * A member closing the tab mid-download is routine, not an incident. Logged at DEBUG and rethrown unchanged, so the
     * status handling above is untouched and nobody is paged for a hang-up.
     */
    @Test
    void reportsAClientHangUpAtDebugRatherThanAsAnIncident() {

        FilterChain hangsUp = (request, response) -> {
            throw new ClientAbortException("broken pipe");
        };

        assertThatThrownBy(
                () -> filterFor(ORGANIZATION, USER).doFilter(request(), new MockHttpServletResponse(), hangsUp))
                .isInstanceOf(ClientAbortException.class);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
            // No handler was matched here, so this is also where a trailing "GET null" would show up.
            assertThat(event.getFormattedMessage()).isEqualTo("Client disconnected while serving GET");
        });
    }

    /**
     * {@code OncePerRequestFilter} skips ASYNC dispatches by default, and the MCP streamable transport does its
     * streaming on one. The initial REQUEST dispatch is covered either way; what the default lost is the second pass,
     * so a {@code /mcp} exchange's streaming half carried no tenant. With the default restored this asserts a null.
     */
    @Test
    void stampsTheTenantOnAnAsyncDispatchToo() throws Exception {

        var seen = new AtomicReference<String>();
        var request = request();
        request.setDispatcherType(DispatcherType.ASYNC);

        filterFor(ORGANIZATION, USER).doFilter(request, new MockHttpServletResponse(),
                (rq, rs) -> seen.set(MDC.get("organization_id")));

        assertThat(seen.get()).isEqualTo(ORGANIZATION);
    }

    @Test
    void logsNothingWhenTheRequestSucceeds() throws Exception {

        filterFor(ORGANIZATION, USER).doFilter(request(), new MockHttpServletResponse(), (rq, rs) -> {
        });

        assertThat(logs.list).isEmpty();
    }

    private static RequestTelemetryFilter filterFor(String organization, String user) {

        return new RequestTelemetryFilter(() -> Optional.of(organization), () -> Optional.of(user));
    }

    private static MockHttpServletRequest request() {

        return new MockHttpServletRequest("GET", HANDLE_BEARING_URI);
    }

    private static FilterChain boom() {

        return (request, response) -> {
            throw new IllegalStateException("boom");
        };
    }
}
