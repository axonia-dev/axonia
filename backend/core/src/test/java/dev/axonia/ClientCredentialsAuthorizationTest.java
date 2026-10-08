package dev.axonia;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import dev.axonia.IntegrationMetrics.Integration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClientCredentialsAuthorizationTest {

    private static final String API = "https://api.example.test";
    private static final String TOKEN_URI = "https://login.example.test/token";

    /** Years from the wall clock, so only a lifetime re-anchored to this clock can expire on it. */
    private static final Instant T0 = Instant.parse("2020-01-01T00:00:00Z");

    private static final ClientRegistration SERVICE = ClientRegistration.withRegistrationId("service")
            .clientId("client").clientSecret("secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).tokenUri(TOKEN_URI).build();

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RestClient.Builder builder = RestClient.builder().baseUrl(API)
            .requestInterceptor(new IntegrationMetrics(registry).interceptor(Integration.ENTRA));
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final SteppableClock clock = new SteppableClock(T0);
    private final ClientCredentialsAuthorization authorization = new ClientCredentialsAuthorization(
            new InMemoryClientRegistrationRepository(SERVICE),
            request -> request.getURI().getPath().startsWith("/open") ? null : "service", clock,
            Duration.ofSeconds(60));
    private final RestClient client = authorization.authorize(builder, UnaryOperator.identity());

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theFirstCallMintsATokenAndCarriesItAsABearer() {

        // given
        server.expect(requestTo(TOKEN_URI)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=client_credentials")))
                .andExpect(content().string(containsString("client_id=client")))
                .andExpect(content().string(containsString("client_secret=secret"))).andRespond(token("t-1", 300));
        expectData("t-1");

        // when
        get("/data");

        // then
        server.verify();
    }

    @Test
    void theTokenIsReusedUntilTheSkewBeforeItExpiresOnTheApplicationClock() {

        // given - a five-minute token with a one-minute skew: kept at exactly four minutes, renewed just after
        server.expect(requestTo(TOKEN_URI)).andRespond(token("t-1", 300));
        expectData("t-1");
        expectData("t-1");
        server.expect(requestTo(TOKEN_URI)).andRespond(token("t-2", 300));
        expectData("t-2");

        // when
        get("/data");
        clock.advance(Duration.ofMinutes(4));
        get("/data");
        clock.advance(Duration.ofNanos(1));
        get("/data");

        // then
        server.verify();
    }

    @Test
    void aTokenWithoutALifetimeIsRenewedOnEveryCall() {

        // given - no expires_in: Spring gives the token one second, inside any skew
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess("""
                {"access_token": "t-1", "token_type": "Bearer"}
                """, MediaType.APPLICATION_JSON));
        expectData("t-1");
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess("""
                {"access_token": "t-2", "token_type": "Bearer"}
                """, MediaType.APPLICATION_JSON));
        expectData("t-2");

        // when
        get("/data");
        get("/data");

        // then
        server.verify();
    }

    @Test
    void aCallNamingNoRegistrationCarriesNoToken() {

        // given
        server.expect(requestTo(API + "/open")).andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess());

        // when
        get("/open");

        // then
        server.verify();
    }

    @Test
    void oneTokenServesEveryoneSignedIn() {

        // given
        server.expect(requestTo(TOKEN_URI)).andRespond(token("t-1", 300));
        expectData("t-1");
        expectData("t-1");

        // when
        signIn("ada");
        get("/data");
        signIn("grace");
        get("/data");

        // then
        server.verify();
    }

    @Test
    void a401DropsTheTokenAndTheNextCallMintsAFreshOne() {

        // given
        server.expect(requestTo(TOKEN_URI)).andRespond(token("t-1", 300));
        server.expect(requestTo(API + "/data")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(TOKEN_URI)).andRespond(token("t-2", 300));
        expectData("t-2");

        // when / then
        assertThatThrownBy(() -> get("/data")).isInstanceOf(HttpClientErrorException.Unauthorized.class);
        get("/data");
        server.verify();
    }

    @Test
    void aForgottenRegistrationMintsAFreshTokenOnItsNextCall() {

        // given
        server.expect(requestTo(TOKEN_URI)).andRespond(token("t-1", 300));
        expectData("t-1");
        server.expect(requestTo(TOKEN_URI)).andRespond(token("t-2", 300));
        expectData("t-2");
        get("/data");

        // when
        authorization.forget("service");

        // then
        get("/data");
        server.verify();
    }

    @Test
    void a403KeepsTheToken() {

        // given
        server.expect(requestTo(TOKEN_URI)).andRespond(token("t-1", 300));
        server.expect(requestTo(API + "/data")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        expectData("t-1");

        // when / then
        assertThatThrownBy(() -> get("/data")).isInstanceOf(HttpClientErrorException.Forbidden.class);
        get("/data");
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("refusedTokenRequests")
    void aRefusedTokenRequestFailsTheCallBeforeItIsSentOrCounted(HttpStatus status, String errorCode,
            Class<? extends Throwable> rootCause) {

        // given - the API call is never expected; 400 is the status RFC 6749 gives a token error and the only one
        // whose error body Spring reads; Entra and Keycloak answer a bad secret with 401, which Spring reports as an
        // unreadable response
        server.expect(requestTo(TOKEN_URI)).andRespond(
                withStatus(status).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_client\"}"));

        // when
        var refused = catchThrowableOfType(ClientAuthorizationException.class, () -> get("/data"));

        // then - the token request is the one failure counted; the call it guarded never went out
        server.verify();
        assertThat(refused.getError().getErrorCode()).isEqualTo(errorCode);
        assertThat(refused).hasRootCauseInstanceOf(rootCause);
        assertThat(calls("failure")).isEqualTo(1.0);
        assertThat(calls("success")).isZero();
    }

    static Stream<Arguments> refusedTokenRequests() {
        return Stream.of(Arguments.of(HttpStatus.BAD_REQUEST, "invalid_client", OAuth2AuthorizationException.class),
                Arguments.of(HttpStatus.UNAUTHORIZED, "invalid_token_response",
                        HttpClientErrorException.Unauthorized.class));
    }

    @Test
    void aTokenResponseWithoutAnAccessTokenFailsTheCall() {

        // given
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess("""
                {"token_type": "Bearer", "expires_in": 300}
                """, MediaType.APPLICATION_JSON));

        // when
        var refused = catchThrowableOfType(ClientAuthorizationException.class, () -> get("/data"));

        // then
        server.verify();
        assertThat(refused.getError().getErrorCode()).isEqualTo("invalid_token_response");
    }

    @Test
    void authorizingLeavesTheBuilderHandedInUntouched() {

        // when - the field above has already authorized this builder once
        var first = authorization.authorize(builder, UnaryOperator.identity());
        var second = authorization.authorize(builder, UnaryOperator.identity());

        // then
        assertThat(tokenInterceptorsOn(builder)).isZero();
        assertThat(tokenInterceptorsOn(first.mutate())).isEqualTo(1);
        assertThat(tokenInterceptorsOn(second.mutate())).isEqualTo(1);
    }

    private static long tokenInterceptorsOn(RestClient.Builder builder) {
        var count = new AtomicLong();
        builder.clone().requestInterceptors(interceptors -> count
                .set(interceptors.stream().filter(OAuth2ClientHttpRequestInterceptor.class::isInstance).count()));
        return count.get();
    }

    private void get(String path) {
        client.get().uri(path).retrieve().toBodilessEntity();
    }

    private void expectData(String token) {
        server.expect(requestTo(API + "/data")).andExpect(header("Authorization", "Bearer " + token))
                .andRespond(withSuccess());
    }

    private static ResponseCreator token(String value, int expiresIn) {
        return withSuccess("""
                {"access_token": "%s", "token_type": "Bearer", "expires_in": %d}
                """.formatted(value, expiresIn), MediaType.APPLICATION_JSON);
    }

    private static void signIn(String name) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(name,
                null, AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    private double calls(String outcome) {
        return registry.find("axonia.integration.calls").tag("outcome", outcome).counters().stream()
                .mapToDouble(Counter::count).sum();
    }
}
