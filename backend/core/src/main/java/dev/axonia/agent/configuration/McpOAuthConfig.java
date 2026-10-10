package dev.axonia.agent.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

import dev.axonia.shared.api.metrics.IntegrationMetrics;
import dev.axonia.shared.api.metrics.IntegrationMetrics.Integration;

@Configuration
@EnableConfigurationProperties(McpOAuthProperties.class)
public class McpOAuthConfig {

    /** Redirects are refused, as for SSO discovery: a document that moved is a document not served at its id. */
    @Bean
    RestClient clientMetadataRestClient(RestClient.Builder builder, HttpClientSettings settings,
            ClientHttpRequestFactoryBuilder<?> factories, IntegrationMetrics metrics) {

        var timeout = Duration.ofSeconds(3);
        var bounded = settings.withRedirects(HttpRedirects.DONT_FOLLOW).withConnectTimeout(timeout)
                .withReadTimeout(timeout);
        return builder.requestFactory(factories.build(bounded))
                .requestInterceptor(metrics.interceptor(Integration.CLIENT_METADATA)).build();
    }

    /**
     * The unauthenticated OAuth surface: the two metadata documents, the token endpoint and the revocation endpoint.
     * Evaluated before every other chain, so Spring Security's own protected-resource filter, which
     * {@code .oauth2ResourceServer(...)} attaches to the default chain with no authorization_servers, never answers the
     * metadata paths, and a stray {@code Authorization: Bearer} header a client attaches to every request is never
     * decoded as a JWT on the way to the token endpoint. Nested so a {@code @WebMvcTest} can import it alone.
     */
    @Configuration
    public static class PublicMcpOAuthSecurityConfig {

        @Bean
        @Order(-1)
        SecurityFilterChain publicMcpOAuthFilterChain(HttpSecurity http) throws Exception {

            return http
                    .securityMatcher("/.well-known/oauth-protected-resource/**",
                            "/.well-known/oauth-authorization-server", "/oauth/token", "/oauth/revoke")
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }
    }
}
