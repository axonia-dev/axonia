package dev.axonia.agent.adapter.in.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.axonia.agent.application.AgentAccess;
import dev.axonia.agent.application.McpEndpoints;
import dev.axonia.agent.configuration.AgentSecurityConfig;
import dev.axonia.identity.api.pat.MemberAccounts;
import dev.axonia.shared.api.ratelimit.RateLimitDecision;
import dev.axonia.shared.api.web.ClientAddress;

/**
 * Authenticates an MCP call from its bearer, an {@code axp_} personal token or an {@code axa_} connection token, and
 * answers every 401 with the challenge that tells an MCP client where to authorize.
 */
public class AgentAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INVALID_TOKEN = "invalid_token";
    private static final String INVALID_REQUEST = "invalid_request";

    /** Matches the repository Spring Security installs for {@code SessionCreationPolicy.STATELESS}. */
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

    private final AgentCredentials credentials;
    private final MemberAccounts members;
    private final AgentSecurityConfig.McpRateLimiters limiters;
    private final AgentAccess access;
    private final McpEndpoints endpoints;

    /** Everything the filter needs, named in one place so a missing or extra dependency fails where it is wired. */
    @Component
    public record Collaborators(AgentCredentials credentials, MemberAccounts members,
            AgentSecurityConfig.McpRateLimiters limiters, AgentAccess access, McpEndpoints endpoints) {
    }

    public AgentAuthenticationFilter(Collaborators collaborators) {

        this.credentials = collaborators.credentials();
        this.members = collaborators.members();
        this.limiters = collaborators.limiters();
        this.access = collaborators.access();
        this.endpoints = collaborators.endpoints();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        var principal = authenticate(request, response);
        if (principal.isEmpty()) {
            return;
        }

        var p = principal.get();
        var call = limiters.calls().tryConsume(p.rateLimitKey());
        if (!call.allowed()) {
            tooManyRequests(response, call);
            return;
        }
        var context = synthesizeContext(p);
        SecurityContextHolder.setContext(context);

        // After the context is set the org GUC resolves, so identity_user is visible under RLS.
        if (!members.isActive(p.organizationId(), p.userId())) {
            SecurityContextHolder.clearContext();
            rejectFailedAuth(request, response, "The account behind this token is no longer active.", INVALID_TOKEN);
            return;
        }

        // A property of the organization OR of the person, never of the credential: a token minted while the surface
        // was open simply stops opening this door when neither carries it any more. Nothing is deleted and nothing
        // is revoked.
        if (!access.isAvailableTo(p.organizationId(), p.userId())) {
            SecurityContextHolder.clearContext();
            rejectFailedAuth(request, response, "The agent surface is not available to this account.", INVALID_TOKEN);
            return;
        }

        // The MCP streamable transport replies with an async ServerResponse, so Tomcat re-runs this chain on an ASYNC
        // dispatch. This filter skips itself there (OncePerRequestFilter#shouldNotFilterAsyncDispatch), and STATELESS
        // keeps nothing in a session, so the context must be persisted where the dispatch can find it: the request.
        // Without this, SecurityContextHolderFilter reloads an empty context and AuthorizationFilter denies access.
        securityContextRepository.saveContext(context, request, response);

        credentials.recordUsage(p);
        chain.doFilter(request, response);
    }

    /** Answers the 401 itself, and returns empty, when the request carries no bearer that authenticates. */
    private Optional<AgentPrincipal> authenticate(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        var header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            rejectFailedAuth(request, response, "A bearer token is required.", null);
            return Optional.empty();
        }
        var token = header.substring(BEARER_PREFIX.length()).strip();
        if (token.isBlank()) {
            rejectFailedAuth(request, response, "A bearer token is required.", INVALID_REQUEST);
            return Optional.empty();
        }
        var principal = credentials.authenticate(token);
        if (principal.isEmpty()) {
            rejectFailedAuth(request, response, "Invalid or expired token.", INVALID_TOKEN);
        }
        return principal;
    }

    /**
     * Builds the security context a valid credential authenticates into: an in-process {@link Jwt} carrying its claims,
     * wrapped in a {@link JwtAuthenticationToken}. Deliberately empty groups: agents act with USER-bound permissions
     * only. Does not install the context; the caller decides when and where.
     */
    private SecurityContext synthesizeContext(AgentPrincipal p) {
        var jwt = Jwt.withTokenValue("agent-session").header("alg", "none").subject(p.userId())
                .claim("org", p.organizationId()).claim("groups", List.of()).claim("scopes", List.copyOf(p.scopes()))
                .claim(p.claim(), p.credentialId()).build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        return context;
    }

    /**
     * Applies the per-address auth-failure rate limit before answering a failed authentication attempt: a 429 when the
     * address has exhausted its budget, the usual 401 problem detail otherwise.
     */
    private void rejectFailedAuth(HttpServletRequest request, HttpServletResponse response, String detail, String error)
            throws IOException {
        var attempt = limiters.authFailures().tryConsume(ClientAddress.of(request));
        if (!attempt.allowed()) {
            tooManyRequests(response, attempt);
            return;
        }
        unauthorized(response, detail, error);
    }

    /**
     * RFC 9728 section 5.1: the challenge names the protected resource metadata, which is how an MCP client finds where
     * to authorize. Per RFC 6750 section 3.1, no error code when no bearer was presented, {@code invalid_request} for a
     * blank one and {@code invalid_token} for one that does not authenticate.
     */
    private void unauthorized(HttpServletResponse response, String detail, String error) throws IOException {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge(error));
        writeProblem(response, "{\"status\":401,\"detail\":\"" + problem.getDetail() + "\"}");
    }

    private String challenge(String error) {

        var metadata = "resource_metadata=\"" + endpoints.resourceMetadata() + "\"";
        return error == null ? "Bearer " + metadata : "Bearer error=\"" + error + "\", " + metadata;
    }

    private void tooManyRequests(HttpServletResponse response, RateLimitDecision decision) throws IOException {

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded.");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        writeProblem(response, "{\"status\":429,\"detail\":\"" + problem.getDetail() + "\"}");
    }

    /** JSON is UTF-8 (RFC 8259); without an explicit encoding the servlet container declares ISO-8859-1. */
    private static void writeProblem(HttpServletResponse response, String body) throws IOException {

        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        response.getWriter().write(body);
    }
}
