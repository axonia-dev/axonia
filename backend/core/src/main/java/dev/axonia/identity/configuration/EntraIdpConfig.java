package dev.axonia.identity.configuration;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver;
import org.springframework.web.client.RestClient;

import dev.axonia.identity.adapter.out.entra.EntraConsentProbe;
import dev.axonia.identity.adapter.out.entra.EntraGraphRegistrations;
import dev.axonia.identity.adapter.out.entra.EntraTokenRequestObservations;
import dev.axonia.shared.api.http.ClientCredentialsAuthorization;
import dev.axonia.shared.api.metrics.IntegrationMetrics;
import dev.axonia.shared.api.metrics.IntegrationMetrics.Integration;

/**
 * Wires the Microsoft Graph and Entra login REST clients. The Graph client serves IdP group discovery and avatar sync,
 * and carries axonia's client-credentials token for the tenant each call names; the login client serves only the
 * consent probe, which mints a token of its own on purpose. Neither client may be used on the request-time
 * authorisation path.
 *
 * <p>
 * Both are built from the auto-configured builder so they inherit the application's connect and read ceilings. The
 * static {@code RestClient.builder()} they used previously bypasses {@code RestClientBuilderConfigurer} and falls back
 * to a request factory with no timeout at all, which left a hung Microsoft endpoint able to hold a thread with no bound
 * on it. The Graph client's token requests go out on a clone of that same builder, so they inherit the ceilings too,
 * and only that clone is observed under the template {@value EntraTokenRequestObservations#TOKEN_URI_TEMPLATE}, so no
 * customer's tenant id reaches a metric or a span.
 */
@Configuration
@EnableConfigurationProperties({EntraGraphProperties.class, AvatarProperties.class})
class EntraIdpConfig {

    /** Entra's tokens live an hour; one is renewed five minutes before it expires. */
    static final Duration GRAPH_TOKEN_RENEWAL = Duration.ofMinutes(5);

    /**
     * The Graph client's tokens, one per tenant, which the consent probe drops when it finds a tenant's consent granted
     * (see {@link EntraConsentProbe}).
     */
    @Bean
    ClientCredentialsAuthorization entraGraphAuthorization(EntraGraphProperties properties, Clock clock) {
        return new ClientCredentialsAuthorization(new EntraGraphRegistrations(properties),
                new RequestAttributeClientRegistrationIdResolver(), clock, GRAPH_TOKEN_RENEWAL);
    }

    /**
     * REST client targeting the Microsoft Graph API ({@code https://graph.microsoft.com}). Its token requests are
     * observed under the meter name every other client request is, read from {@link ObservationProperties} as Boot's
     * own convention reads it.
     */
    @Bean
    RestClient entraGraphRestClient(RestClient.Builder builder, IntegrationMetrics metrics,
            ClientCredentialsAuthorization entraGraphAuthorization, ObservationProperties observations) {
        return entraGraphAuthorization.authorize(
                builder.baseUrl("https://graph.microsoft.com")
                        .requestInterceptor(metrics.interceptor(Integration.ENTRA)),
                tokenRequests -> tokenRequests.observationConvention(
                        new EntraTokenRequestObservations(observations.getHttp().getClient().getRequests().getName())));
    }

    /**
     * REST client targeting the Microsoft login endpoint ({@code https://login.microsoftonline.com}) for the consent
     * probe's own token request.
     */
    @Bean
    RestClient entraLoginRestClient(RestClient.Builder builder, IntegrationMetrics metrics) {
        return builder.baseUrl("https://login.microsoftonline.com")
                .requestInterceptor(metrics.interceptor(Integration.ENTRA)).build();
    }
}
