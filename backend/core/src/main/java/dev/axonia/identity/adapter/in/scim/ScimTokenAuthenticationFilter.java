package dev.axonia.identity.adapter.in.scim;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.axonia.identity.application.scim.ScimTokens;
import dev.axonia.identity.configuration.ScimSecurityConfig;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code Authorization: Bearer <token>}, authenticates it against {@link ScimTokens}, and on success populates
 * the {@link org.springframework.security.core.context.SecurityContext} with a {@link ScimAuthenticationToken}. On
 * failure, writes a SCIM-shaped 401 and terminates the filter chain.
 *
 * <p>
 * Not annotated with {@code @Component}: declared as a {@code @Bean} in {@link ScimSecurityConfig} and wired into the
 * SCIM-only {@link org.springframework.security.web.SecurityFilterChain}. A {@code FilterRegistrationBean} in that
 * config prevents the servlet container from also registering this filter globally.
 */
public class ScimTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SCIM_JSON = "application/scim+json";

    private final ScimTokens scimTokens;
    private final JsonMapper jsonMapper;

    public ScimTokenAuthenticationFilter(ScimTokens scimTokens, JsonMapper jsonMapper) {
        this.scimTokens = scimTokens;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        var token = header.substring(BEARER_PREFIX.length()).strip();
        if (token.isBlank()) {
            writeUnauthorized(response, "Authorization header contains no bearer token");
            return;
        }

        var principal = scimTokens.authenticate(token);
        if (principal.isEmpty()) {
            writeUnauthorized(response, "Invalid or revoked SCIM bearer token");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new ScimAuthenticationToken(principal.get()));
        // Record usage AFTER the SecurityContext (and thus the Organization GUC) is set, so the
        // scim_token_org_write RLS policy matches the caller's own row.
        scimTokens.recordUsage(principal.get().organizationId(), token);
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String detail) throws IOException {
        var error = ScimError.of(HttpStatus.UNAUTHORIZED, detail);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(SCIM_JSON);
        response.getWriter().write(jsonMapper.writeValueAsString(error));
    }
}
