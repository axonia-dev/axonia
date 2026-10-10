package dev.axonia.shared.adapter.in.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses any request that did not arrive through the edge.
 *
 * <p>
 * The origin is publicly reachable at its platform hostname. Without this gate, anyone who learns that hostname could
 * stamp their own client address and pick their own rate-limit bucket, so the gate is what makes
 * {@link dev.axonia.shared.api.web.ClientAddress} trustworthy.
 *
 * <p>
 * The platform's health probe is the one caller that legitimately arrives without a stamp: it is service-internal and
 * never traverses the edge. Every other route into the application goes through the edge. The exemption is an
 * exact-match set rather than a path prefix because {@code getRequestURI()} is the verbatim request line, not the
 * normalized path the servlet mapping uses: a prefix test would exempt
 * {@code /actuator/health/../../axonia/communities}, which maps somewhere else entirely. Matching whole paths means the
 * gate never has to reason about traversal, encoding, or another filter having normalized the URI first.
 *
 * <p>
 * The refusal is deliberately mute. It says nothing about which secret was expected, whether one was supplied, or how
 * close a supplied one was.
 */
public class EdgeGateFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Axonia-Edge-Auth";

    private static final Set<String> HEALTH_PATHS = Set.of("/actuator/health", "/actuator/health/liveness",
            "/actuator/health/readiness");
    private static final String REFUSAL = "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403}";

    private final List<byte[]> secrets;

    public EdgeGateFilter(List<String> secrets) {

        this.secrets = secrets.stream().map(secret -> secret.getBytes(StandardCharsets.UTF_8)).toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (isHealthProbe(request) || isFromOurEdge(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(REFUSAL);
    }

    private static boolean isHealthProbe(HttpServletRequest request) {

        return HEALTH_PATHS.contains(request.getRequestURI());
    }

    /** Constant-time across every accepted secret: no early exit tells a caller it guessed a prefix. */
    private boolean isFromOurEdge(HttpServletRequest request) {

        var supplied = request.getHeader(HEADER);
        if (supplied == null) {
            return false;
        }
        var candidate = supplied.getBytes(StandardCharsets.UTF_8);
        var matched = false;
        for (var secret : secrets) {
            matched |= MessageDigest.isEqual(secret, candidate);
        }
        return matched;
    }
}
