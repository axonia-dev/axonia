package dev.axonia.selfhosted.configuration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.nimbusds.jose.jwk.RSAKey;

import dev.axonia.SteppableClock;
import dev.axonia.selfhosted.LocalIssuer;
import dev.axonia.selfhosted.MockOidcIssuer;
import dev.axonia.selfhosted.adapter.in.security.IssuerKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SelfHostedSecurityConfigTest {

    private static final String ISSUER = "https://login.example.com/tenant/v2.0";

    private static final String AUDIENCE = "api://axonia";

    private static final String ENTRA_ISSUER = "https://login.microsoftonline.com/"
            + SelfHostedPropertiesMother.ENTRA_TENANT + "/v2.0";

    private static final RSAKey KEY = LocalIssuer.key("signing");

    private static final OAuth2TokenValidator<Jwt> VALIDATOR = SelfHostedSecurityConfig.validator(oidc(ISSUER),
            Clock.systemUTC());

    private final List<IssuerKeys> keysBuilt = new ArrayList<>();

    @AfterEach
    void closeTheKeys() throws IOException {

        for (var keys : keysBuilt) {
            keys.close();
        }
    }

    @Test
    void acceptsATokenOfTheIssuerForTheAudience() {

        // when
        var actual = VALIDATOR.validate(token(ISSUER, List.of("another-application", AUDIENCE), inAnHour()));

        // then
        assertThat(actual.hasErrors()).isFalse();
    }

    @Test
    void refusesATokenForAnotherAudience() {

        // when
        var actual = VALIDATOR.validate(token(ISSUER, List.of("another-application"), inAnHour()));

        // then - named with what the token says and the setting it was held against
        assertThat(actual.getErrors()).extracting(OAuth2Error::getDescription)
                .containsExactly("The token's audience [another-application] does not include the configured "
                        + "AXONIA_SELFHOSTED_OIDC_AUDIENCE api://axonia.");
    }

    @Test
    void refusesATokenWithNoAudience() {

        // when
        var actual = VALIDATOR.validate(Jwt.withTokenValue("t").header("alg", "RS256").issuer(ISSUER).subject("alice")
                .issuedAt(Instant.now()).expiresAt(inAnHour()).build());

        // then
        assertThat(actual.getErrors()).extracting(OAuth2Error::getDescription).containsExactly(
                "The token's audience [] does not include the configured AXONIA_SELFHOSTED_OIDC_AUDIENCE api://axonia.");
    }

    @Test
    void refusesATokenOfAnotherIssuer() {

        // when
        var actual = VALIDATOR
                .validate(token("https://login.example.com/another-tenant/v2.0", List.of(AUDIENCE), inAnHour()));

        // then
        assertThat(actual.getErrors()).extracting(OAuth2Error::getDescription)
                .containsExactly("The token's issuer https://login.example.com/another-tenant/v2.0 is not the "
                        + "configured AXONIA_SELFHOSTED_OIDC_ISSUER_URI https://login.example.com/tenant/v2.0.");
    }

    @Test
    void refusesAnExpiredToken() {

        // when
        var actual = VALIDATOR.validate(token(ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(7200)));

        // then
        assertThat(actual.getErrors()).extracting(OAuth2Error::getDescription)
                .anySatisfy(description -> assertThat(description).contains("expired"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"JWT", "at+jwt", "application/at+jwt"})
    void acceptsATokenTypedAsAJwtOrAnAccessToken(String type) {

        // when
        var actual = VALIDATOR.validate(builder(ISSUER, List.of(AUDIENCE), inAnHour()).header("typ", type).build());

        // then
        assertThat(actual.hasErrors()).isFalse();
    }

    @Test
    void refusesATokenOfAnotherType() {

        // when
        var actual = VALIDATOR
                .validate(builder(ISSUER, List.of(AUDIENCE), inAnHour()).header("typ", "logout+jwt").build());

        // then
        assertThat(actual.getErrors()).extracting(OAuth2Error::getDescription)
                .anySatisfy(description -> assertThat(description).contains("typ"));
    }

    @Test
    void refusesATokenIssuedToAnApplication() {

        // when
        var actual = VALIDATOR.validate(builder(ISSUER, List.of(AUDIENCE), inAnHour()).claim("idtyp", "app").build());

        // then
        assertThat(actual.getErrors()).extracting(OAuth2Error::getDescription)
                .containsExactly("The token was issued to an application, not to a person: its idtyp is app.");
    }

    @Test
    void acceptsATokenIssuedToAUser() {

        // when
        var actual = VALIDATOR.validate(builder(ISSUER, List.of(AUDIENCE), inAnHour()).claim("idtyp", "user").build());

        // then
        assertThat(actual.hasErrors()).isFalse();
    }

    @Test
    void refusesAnEntraTokenWithNoDelegatedScope() {

        // given
        var validator = SelfHostedSecurityConfig.validator(oidc(ENTRA_ISSUER), Clock.systemUTC());

        // when
        var actual = validator.validate(token(ENTRA_ISSUER, List.of(AUDIENCE), inAnHour()));

        // then
        assertThat(actual.getErrors()).extracting(OAuth2Error::getDescription).containsExactly(
                "The token was issued to an application, not to a person: an Entra token with no delegated scp.");
    }

    @Test
    void acceptsAnEntraTokenWithADelegatedScope() {

        // given
        var validator = SelfHostedSecurityConfig.validator(oidc(ENTRA_ISSUER), Clock.systemUTC());

        // when
        var actual = validator
                .validate(builder(ENTRA_ISSUER, List.of(AUDIENCE), inAnHour()).claim("scp", "axonia").build());

        // then
        assertThat(actual.hasErrors()).isFalse();
    }

    @Test
    void anotherIssuersTokenNeedsNoDelegatedScope() {

        // when
        var actual = VALIDATOR.validate(token(ISSUER, List.of(AUDIENCE), inAnHour()));

        // then
        assertThat(actual.hasErrors()).isFalse();
    }

    @Test
    void theDecoderGivesUpOnAnIssuerThatNeverAnswers() throws IOException {

        try (var silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {

            // given
            var issuer = "http://127.0.0.1:" + silent.getLocalPort() + "/silent";
            var decoder = decoder(issuer, AUDIENCE);

            // when
            var actual = assertTimeoutPreemptively(Duration.ofSeconds(15),
                    () -> catchThrowable(() -> decoder.decode(LocalIssuer.token(issuer, KEY))));

            // then
            assertThat(actual).hasCauseInstanceOf(IssuerKeys.Unavailable.class).rootCause()
                    .isInstanceOf(HttpTimeoutException.class);
        }
    }

    @Test
    void tokensArrivingWhileTheIssuerStaysUnreachableAreRefusedAtOnce() throws IOException {

        try (var silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                var requests = Executors.newVirtualThreadPerTaskExecutor()) {

            // given - the first token spends the one attempt the interval allows
            var issuer = "http://127.0.0.1:" + silent.getLocalPort() + "/silent";
            var decoder = decoder(issuer, AUDIENCE);
            var token = LocalIssuer.token(issuer, KEY);
            assertTimeoutPreemptively(Duration.ofSeconds(15), () -> assertThatThrownBy(() -> decoder.decode(token))
                    .hasCauseInstanceOf(IssuerKeys.Unavailable.class));

            // when
            var refusals = IntStream.range(0, 20).mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                var started = System.nanoTime();
                assertThatThrownBy(() -> decoder.decode(token)).hasCauseInstanceOf(IssuerKeys.Unavailable.class);
                return Duration.ofNanos(System.nanoTime() - started);
            }, requests)).toList();

            // then
            assertThat(refusals).map(CompletableFuture::join)
                    .allSatisfy(refusal -> assertThat(refusal).isLessThan(Duration.ofMillis(500)));
        }
    }

    @Test
    void everyCallToTheIssuerGivesUpAfterFiveSecondsToConnectAndTenInAll() {

        // when
        var actual = SelfHostedSecurityConfig.issuerRequests();

        // then - the read timeout of the JDK client bounds the whole call, its body included
        assertThat(actual).isInstanceOf(JdkClientHttpRequestFactory.class);
        assertThat(ReflectionTestUtils.getField(actual, "readTimeout")).isEqualTo(Duration.ofSeconds(10));
        assertThat(ReflectionTestUtils.getField(actual, "httpClient")).asInstanceOf(type(HttpClient.class))
                .satisfies(client -> {
                    assertThat(client.connectTimeout()).contains(Duration.ofSeconds(5));
                    assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NORMAL);
                });
    }

    @Test
    void aRefreshThatRunsIntoTheCallsDeadlineTwiceStillEndsBeforeTheKeysLapse() {

        // given
        var lifetimes = IssuerKeys.Lifetimes.STANDARD;

        // when
        var deadline = (Duration) ReflectionTestUtils.getField(SelfHostedSecurityConfig.issuerRequests(),
                "readTimeout");

        // then
        assertThat(deadline.multipliedBy(2)).isLessThan(lifetimes.refreshAhead().plus(lifetimes.refreshWait()));
    }

    @Test
    void theDecoderTellsWhetherATokenExpiredByTheApplicationsClock() {

        try (var issuer = LocalIssuer.start()) {

            // given - the application's clock two hours past the issuer's
            var decoder = decoder(issuer.uri(), LocalIssuer.AUDIENCE,
                    new SteppableClock(Instant.now().plus(Duration.ofHours(2))));

            // when / then
            assertThatThrownBy(() -> decoder.decode(issuer.token(issuer.first)))
                    .isInstanceOf(JwtValidationException.class).hasMessageContaining("expired");
        }
    }

    @Test
    void theDecoderAsksItsIssuerWithoutOfferingAnHttp2Upgrade() {

        try (var issuer = LocalIssuer.start()) {

            // given
            var decoder = decoder(issuer.uri(), LocalIssuer.AUDIENCE);

            // when
            decoder.decode(issuer.token(issuer.first));

            // then
            assertThat(issuer.requestHeaders).hasSize(2).extracting(headers -> headers.getFirst("Upgrade"))
                    .containsOnlyNulls();
        }
    }

    @Test
    void closingTheApplicationStopsTheDecodersKeyRefreshes() {

        try (var issuer = LocalIssuer.start(); var context = new AnnotationConfigApplicationContext()) {

            // given
            context.registerBean(SelfHostedProperties.class, () -> properties(issuer.uri(), LocalIssuer.AUDIENCE));
            context.registerBean(Clock.class, Clock::systemUTC);
            context.register(SelfHostedSecurityConfig.class);
            context.refresh();
            var decoder = context.getBean(JwtDecoder.class);
            decoder.decode(issuer.token(issuer.first));

            // when
            context.close();

            // then
            assertThat(decoder).extracting("jwtProcessor.JWSKeySelector.JWKSource.held.JWKSetSource")
                    .satisfies(source -> {
                        assertThat(source).extracting("executorService", type(ExecutorService.class)).returns(true,
                                ExecutorService::isShutdown);
                        assertThat(source).extracting("scheduledExecutorService", type(ExecutorService.class))
                                .returns(true, ExecutorService::isShutdown);
                    });
        }
    }

    @Test
    void theDecoderReachesTheIssuerOnlyWhenATokenArrives() {

        // given
        var issuer = "http://127.0.0.1:9/nowhere";
        var decoder = decoder(issuer, AUDIENCE);

        // when / then
        assertThatThrownBy(() -> decoder.decode(LocalIssuer.token(issuer, KEY)))
                .hasCauseInstanceOf(IssuerKeys.Unavailable.class);
    }

    @Test
    void theDecoderReadsATokenItsIssuerSignedForTheAudience() {

        // given
        var issuer = MockOidcIssuer.shared();
        var decoder = decoder(issuer.issuerUri(), MockOidcIssuer.AUDIENCE);

        // when
        var actual = decoder.decode(issuer.token("alice", Map.of()));

        // then
        assertThat(actual.getSubject()).isEqualTo("alice");
    }

    @Test
    void theDecoderValidatesTheAudienceOfATokenItsIssuerSigned() {

        // given
        var issuer = MockOidcIssuer.shared();
        var decoder = decoder(issuer.issuerUri(), MockOidcIssuer.AUDIENCE);
        var forAnotherApplication = issuer.token("alice", Map.of("aud", "another-application"));

        // when / then
        assertThatThrownBy(() -> decoder.decode(forAnotherApplication)).isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("The token's audience [another-application] does not include the configured "
                        + "AXONIA_SELFHOSTED_OIDC_AUDIENCE " + MockOidcIssuer.AUDIENCE + ".");
    }

    private JwtDecoder decoder(String issuer, String audience) {

        return decoder(issuer, audience, Clock.systemUTC());
    }

    private JwtDecoder decoder(String issuer, String audience, Clock clock) {

        var config = new SelfHostedSecurityConfig();
        var properties = properties(issuer, audience);
        var keys = config.issuerKeys(properties, clock);
        keysBuilt.add(keys);
        return config.jwtDecoder(keys, properties, clock);
    }

    private static SelfHostedProperties properties(String issuer, String audience) {

        return new SelfHostedProperties(new SelfHostedProperties.Organization("acme", "Acme"), "owners",
                new SelfHostedProperties.Oidc(issuer, audience, "sub", "groups", List.of("email"), "name"));
    }

    private static SelfHostedProperties.Oidc oidc(String issuer) {

        return properties(issuer, AUDIENCE).oidc();
    }

    private static Jwt token(String issuer, List<String> audience, Instant expiresAt) {

        return builder(issuer, audience, expiresAt).build();
    }

    private static Jwt.Builder builder(String issuer, List<String> audience, Instant expiresAt) {

        return Jwt.withTokenValue("t").header("alg", "RS256").issuer(issuer).subject("alice").audience(audience)
                .issuedAt(expiresAt.minusSeconds(600)).expiresAt(expiresAt);
    }

    private static Instant inAnHour() {

        return Instant.now().plusSeconds(3600);
    }
}
