package dev.axonia.shared.adapter.in.web;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.DisconnectedClientHelper;

import dev.axonia.shared.api.tenancy.OrganizationResolver;
import dev.axonia.shared.api.tenancy.SubjectResolver;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;

/**
 * Two request-scoped jobs that both belong to the same try/finally, which is why they are one filter rather than two.
 *
 * <p>
 * It stamps the tenant onto the MDC <em>and</em> onto the server span, so a log line and the trace it belongs to join
 * on tenant as well as on trace id. Both identifiers are opaque: an Organization id, and the internal user UUID that
 * {@link SubjectResolver} reads off the token's {@code sub}. Nothing here reaches the database - a per-request lookup
 * to decorate telemetry would be a tax on every call - and nothing else is stamped, which is what lets the log
 * appender's allowlist name exactly {@code organization_id} and {@code user_id} and drop everything else.
 *
 * <p>
 * The span write is inert wherever tracing is off: {@link Span#current()} answers with a non-recording span, whose
 * {@code setAttribute} returns immediately. DEV and the whole suite therefore pay nothing for it.
 *
 * <p>
 * Both stamps sit inside the {@code try} so the {@code finally} covers them. A resolver is an injected seam and may one
 * day throw - {@code CurrentToken.organization()} does - and a throw between the two stamps would otherwise leave
 * {@code organization_id} on a pooled container thread, attributing the next request it serves to the previous tenant.
 *
 * <p>
 * It is the only place <em>axonia</em> logs an exception escaping the dispatcher. A
 * {@code @ExceptionHandler(Exception.class)} on {@code GlobalExceptionHandler} would have been the obvious alternative
 * and is wrong here: it would convert exceptions that currently reach the container into handled responses, changing
 * what clients receive, and {@code spring.mvc.problemdetails.enabled=false} in PROD means that change would not be
 * cosmetic. Logging and rethrowing leaves every status code exactly as it was.
 *
 * <p>
 * The rethrow does mean the record is not the only one: the exception travels on to Tomcat, whose
 * {@code StandardWrapperValve} logs its own canonical "Servlet.service() threw exception" line through the JUL bridge.
 * That copy carries the same stack and no tenant, because this filter's {@code finally} has already cleared the MDC by
 * then. So a 5xx ships two ERROR records in PROD, one tenanted and one not. Silencing the container logger would mean
 * pinning a version-dependent Tomcat internal name in {@code logback-spring.xml}, which stops matching after an upgrade
 * with nothing to notice it, and that is a worse defect than a duplicate on a rare path.
 *
 * <p>
 * An exception raised by a <em>handler</em> does not arrive here when {@code GlobalExceptionHandler} covers it:
 * {@code DispatcherServlet.doDispatch} resolves it, renders the {@code ProblemDetail} and returns normally, so the
 * throw never reaches this frame. That is correct - a 404 is an outcome, not an incident. The same exception type
 * thrown by a filter downstream of this one ({@code OrganizationLifecycleFilter} and friends all run behind it) has no
 * dispatcher to convert it and would be reported as an incident. None of them throws one today.
 *
 * <p>
 * A client that hangs up mid-response is not an incident either. {@code ClientAbortException} is an
 * {@link IOException}, so it would otherwise arrive here with a stack trace and a severity nobody can act on - a member
 * closing the tab during a privacy export, or an MCP stream ending. {@link DisconnectedClientHelper} is Spring's own
 * classifier for exactly that, and those are logged at DEBUG and rethrown unchanged.
 *
 * <p>
 * The escape line names the <em>templated</em> route, never {@code getRequestURI()}. Paths in this product carry member
 * handles, and a log record shipped to a third-party backend leaks one exactly as a span attribute would. The pattern
 * is read in the catch block rather than up front on purpose: the dispatcher only publishes it once it has matched a
 * handler, so it is present by the time the stack unwinds and absent precisely when the failure happened before
 * matching - and then the method alone is logged, because the alternative is the raw URI. Both severities format that
 * target through one expression rather than a branch each, so no future third case can reintroduce a trailing
 * {@code null}.
 */
@Slf4j
public class RequestTelemetryFilter extends OncePerRequestFilter {

    private static final String ORGANIZATION_KEY = "organization_id";

    private static final String USER_KEY = "user_id";

    private final OrganizationResolver organizations;

    private final SubjectResolver subjects;

    public RequestTelemetryFilter(OrganizationResolver organizations, SubjectResolver subjects) {

        this.organizations = organizations;
        this.subjects = subjects;
    }

    /**
     * Async dispatches are filtered too, unlike the {@link OncePerRequestFilter} default. The MCP streamable transport
     * replies with an async {@code ServerResponse}, so Tomcat re-runs the chain on an ASYNC dispatch. The initial
     * REQUEST dispatch of such a call is covered either way - this filter is nested inside the security chain and
     * stamps there like anywhere else - so what the default left uncovered is precisely the second pass: the streaming
     * half of a {@code /mcp} exchange carried no tenant on its records, and a failure raised there went unlogged. The
     * {@code finally} is what makes covering it safe: the second pass stamps and clears on its own thread like any
     * other.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {

        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        try {
            stamp(ORGANIZATION_KEY, organizations.currentOrganization());
            stamp(USER_KEY, subjects.currentSubject());
            chain.doFilter(request, response);
        } catch (RuntimeException | ServletException | IOException e) {
            logEscape(request, e);
            throw e;
        } finally {
            // Threads are pooled, and a key left behind attributes the next request's log lines to the previous
            // request's tenant.
            MDC.remove(ORGANIZATION_KEY);
            MDC.remove(USER_KEY);
        }
    }

    private static void stamp(String key, Optional<String> value) {

        value.filter(text -> !text.isBlank()).ifPresent(text -> {
            MDC.put(key, text);
            Span.current().setAttribute(key, text);
        });
    }

    private static void logEscape(HttpServletRequest request, Exception escaped) {

        var target = target(request);
        if (DisconnectedClientHelper.isClientDisconnectedException(escaped)) {
            log.debug("Client disconnected while serving {}", target, escaped);
        } else {
            log.error("Unhandled failure serving {}", target, escaped);
        }
    }

    private static String target(HttpServletRequest request) {

        var route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return route == null ? request.getMethod() : request.getMethod() + " " + route;
    }
}
