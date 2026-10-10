package dev.axonia.agent.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import dev.axonia.agent.adapter.in.security.AgentAuthenticationFilter;
import dev.axonia.shared.api.ratelimit.RateLimiter;
import dev.axonia.shared.api.ratelimit.RateLimiters;

/**
 * Security configuration for the MCP surface ({@code /mcp/**}).
 *
 * <p>
 * Registers a dedicated {@link SecurityFilterChain} at {@code @Order(0)}, evaluated before the SCIM chain
 * ({@code @Order(1)}), the ops chain ({@code @Order(2)}), and the application's default JWT chain ({@code @Order(3)}),
 * with an explicit {@code securityMatcher} so the MCP surface is fully isolated. Authentication is handled by
 * {@link AgentAuthenticationFilter}: a valid {@code axp_} personal token or {@code axa_} connection token synthesizes
 * an in-process {@link org.springframework.security.oauth2.jwt.Jwt} wrapped in a
 * {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken}, placed in the
 * security context, enabling organization-aware RLS and reuse of every member service unchanged.
 */
@Configuration
@EnableConfigurationProperties(McpRateLimitProperties.class)
public class AgentSecurityConfig {

    /**
     * The MCP surface's independent budgets, carried as one bean instead of several same-typed {@link RateLimiter}
     * beans. Same-typed beans of a shared root-package type would only be tellable apart by Spring's last-resort
     * parameter-name-to-bean-name fallback: a new {@code RateLimiter} injection point elsewhere would fail the whole
     * context with {@code NoUniqueBeanDefinitionException}, and transposing two at any call site would compile and boot
     * cleanly while silently swapping which budget guards what. A record with named components makes both mistakes
     * impossible instead of merely unlikely.
     */
    public record McpRateLimiters(RateLimiter calls, RateLimiter authFailures, RateLimiter tokenRequests,
            RateLimiter consentRequests) {
    }

    @Bean
    McpRateLimiters mcpRateLimiters(RateLimiters rateLimiters, McpRateLimitProperties rateLimit) {

        return new McpRateLimiters(rateLimiters.create("mcp-calls", rateLimit.callsPerMinute(), Duration.ofMinutes(1)),
                rateLimiters.create("mcp-auth-failure", rateLimit.authFailuresPerMinute(), Duration.ofMinutes(1)),
                rateLimiters.create("mcp-oauth-token", rateLimit.tokenRequestsPerMinute(), Duration.ofMinutes(1)),
                rateLimiters.create("mcp-consent", rateLimit.consentRequestsPerMinute(), Duration.ofMinutes(1)));
    }

    /**
     * Built here rather than component-scanned: a {@code @WebMvcTest} slice picks up every {@code Filter} component,
     * and none of them holds this filter's dependencies.
     */
    @Bean
    AgentAuthenticationFilter agentAuthenticationFilter(AgentAuthenticationFilter.Collaborators collaborators) {

        return new AgentAuthenticationFilter(collaborators);
    }

    /**
     * Prevents Spring Boot from registering {@link AgentAuthenticationFilter} as a global servlet filter. Without this,
     * the filter would run for every request (not just {@code /mcp/**}) and would also execute twice for MCP requests
     * (once globally, once inside the security filter chain).
     */
    @Bean
    FilterRegistrationBean<AgentAuthenticationFilter> agentFilterRegistration(AgentAuthenticationFilter filter) {

        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // prevent global servlet registration + double execution
        return registration;
    }

    @Bean
    @Order(0)
    SecurityFilterChain mcpFilterChain(HttpSecurity http, AgentAuthenticationFilter filter) throws Exception {

        return http.securityMatcher("/mcp/**").csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
