package dev.axonia.identity.configuration;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import dev.axonia.identity.adapter.in.scim.ScimAuthenticationToken;
import dev.axonia.identity.adapter.in.scim.ScimTokenAuthenticationFilter;
import dev.axonia.identity.application.scim.ScimTokens;
import tools.jackson.databind.json.JsonMapper;

/**
 * Security configuration for the SCIM 2.0 provisioning surface ({@code /scim/v2/**}).
 *
 * <p>
 * Registers a dedicated {@link SecurityFilterChain} at {@code @Order(1)} — evaluated before the application's default
 * JWT chain — with an explicit {@code securityMatcher} so the SCIM surface is fully isolated. Authentication is handled
 * by {@link ScimTokenAuthenticationFilter}: a valid SCIM bearer token places a {@link ScimAuthenticationToken} (which
 * implements {@link dev.axonia.shared.api.tenancy.OrganizationBoundAuthentication}) in the security context, enabling
 * organization-aware RLS for any subsequent database operations.
 */
@Configuration
public class ScimSecurityConfig {

    /**
     * The SCIM bearer-token filter. Declared here (not as {@code @Component}) so the servlet container does not
     * register it globally; the {@link FilterRegistrationBean} below explicitly disables that auto-registration.
     */
    @Bean
    ScimTokenAuthenticationFilter scimTokenAuthenticationFilter(ScimTokens scimTokens, JsonMapper jsonMapper) {
        return new ScimTokenAuthenticationFilter(scimTokens, jsonMapper);
    }

    /**
     * Prevents Spring Boot from registering {@link ScimTokenAuthenticationFilter} as a global servlet filter. Without
     * this, the filter would run for every request (not just {@code /scim/v2/**}) and would also execute twice for SCIM
     * requests (once globally, once inside the security filter chain).
     */
    @Bean
    FilterRegistrationBean<ScimTokenAuthenticationFilter> scimFilterRegistration(ScimTokenAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    SecurityFilterChain scimFilterChain(HttpSecurity http, ScimTokenAuthenticationFilter scimFilter) throws Exception {
        return http.securityMatcher("/scim/v2/**").csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(scimFilter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
