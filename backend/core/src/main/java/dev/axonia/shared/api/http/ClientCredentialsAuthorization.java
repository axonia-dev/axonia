package dev.axonia.shared.api.http;

import java.time.Clock;
import java.time.Duration;
import java.util.function.UnaryOperator;

import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor.ClientRegistrationIdResolver;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Puts a client-credentials access token on every request a {@link RestClient} sends to an API that wants one, through
 * Spring Security's OAuth2 Client: a token is minted on the first call for its registration, kept until
 * {@code clockSkew} before it expires, and dropped when the API answers 401, so no adapter ever handles one.
 *
 * <p>
 * The principal is fixed. The interceptor would otherwise take the signed-in person from the security context and mint
 * one token per person for a grant that has no user at all.
 *
 * <p>
 * The token request goes out on its own clone of the builder handed in, passed through {@code tokenRequests}: it
 * carries the same timeouts and integration meter as the calls it authorizes, is never itself intercepted, and can be
 * customised apart from them, as Entra's is observed under its tenant template. The API client is built from another
 * clone, so the builder handed in is never changed and two calls on one builder never stack two interceptors. The
 * interceptor goes first in the chain, so a call refused before it is sent is not counted as one.
 *
 * <p>
 * A minted token's lifetime is re-anchored to {@code clock}: Spring stamps {@code issuedAt} from the wall clock, and
 * renewal must follow the application's clock like every other deadline.
 *
 * <p>
 * Every client one instance authorizes shares its one token store, and {@link #forget} drops a registration's token
 * from it, so the next call mints a fresh one.
 *
 * <p>
 * None of its parts is a Spring bean. A {@code ClientRegistrationRepository} or authorized-client manager bean is what
 * Spring Security's own configuration looks for to wire OAuth2 login support, and none of that applies to a service
 * account. An instance may be a bean itself: it is none of those types.
 */
public final class ClientCredentialsAuthorization {

    private static final Authentication SERVICE = new AnonymousAuthenticationToken("axonia-service", "axonia-service",
            AuthorityUtils.createAuthorityList("ROLE_SERVICE"));

    private final ClientRegistrationRepository registrations;
    private final ClientRegistrationIdResolver registrationIds;
    private final Clock clock;
    private final Duration clockSkew;
    private final InMemoryOAuth2AuthorizedClientService store;

    public ClientCredentialsAuthorization(ClientRegistrationRepository registrations,
            ClientRegistrationIdResolver registrationIds, Clock clock, Duration clockSkew) {
        this.registrations = registrations;
        this.registrationIds = registrationIds;
        this.clock = clock;
        this.clockSkew = clockSkew;
        this.store = new InMemoryOAuth2AuthorizedClientService(registrations);
    }

    public RestClient authorize(RestClient.Builder api, UnaryOperator<RestClient.Builder> tokenRequests) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, store);
        manager.setAuthorizedClientProvider(new AnchoredToClock(minting(tokenRequests.apply(api.clone())), clock));
        var interceptor = new OAuth2ClientHttpRequestInterceptor(manager);
        interceptor.setClientRegistrationIdResolver(registrationIds);
        interceptor.setPrincipalResolver(request -> SERVICE);
        interceptor
                .setAuthorizationFailureHandler(OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(store));
        return api.clone().requestInterceptors(interceptors -> interceptors.addFirst(interceptor)).build();
    }

    /** Drops the token kept for {@code registrationId}, so the next call naming it mints a fresh one. */
    public void forget(String registrationId) {
        store.removeAuthorizedClient(registrationId, SERVICE.getName());
    }

    private OAuth2AuthorizedClientProvider minting(RestClient.Builder tokenRequests) {
        var tokens = new RestClientClientCredentialsTokenResponseClient();
        tokens.setRestClient(tokenRequests.configureMessageConverters(converters -> {
            converters.addCustomConverter(new FormHttpMessageConverter());
            converters.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter());
        }).defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build());
        var provider = new ClientCredentialsOAuth2AuthorizedClientProvider();
        provider.setAccessTokenResponseClient(tokens);
        provider.setClock(clock);
        provider.setClockSkew(clockSkew);
        return provider;
    }

    private record AnchoredToClock(OAuth2AuthorizedClientProvider minting,
            Clock clock) implements OAuth2AuthorizedClientProvider {

        @Override
        public OAuth2AuthorizedClient authorize(OAuth2AuthorizationContext context) {
            var minted = minting.authorize(context);
            if (minted == null) {
                return null;
            }
            var token = minted.getAccessToken();
            var issuedAt = clock.instant();
            var expiresAt = issuedAt.plus(Duration.between(token.getIssuedAt(), token.getExpiresAt()));
            return new OAuth2AuthorizedClient(minted.getClientRegistration(), minted.getPrincipalName(),
                    new OAuth2AccessToken(token.getTokenType(), token.getTokenValue(), issuedAt, expiresAt,
                            token.getScopes()));
        }
    }
}
