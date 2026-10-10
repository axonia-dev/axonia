package dev.axonia.selfhosted.configuration;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

import dev.axonia.selfhosted.adapter.in.security.IssuerKeys;
import dev.axonia.selfhosted.domain.EntraIssuer;

/**
 * The one issuer whose access tokens the installation accepts, for the one audience it is registered as, both read from
 * {@link SelfHostedProperties} so that no other setting can point the decoder elsewhere. The issuer's discovery
 * document and keys are fetched when the first token arrives rather than at start, so an identity provider that is
 * briefly unreachable delays sign-in instead of refusing the start. Each call to it waits at most five seconds to
 * connect and ten in all, and {@link IssuerKeys} decides what a token whose key it cannot have is answered. A token's
 * expiry is read on the application's clock.
 *
 * <p>
 * A token is accepted with the {@code typ} of a plain JWT or of an RFC 9068 access token, and only when it was issued
 * to a person: an app-only token says so in {@code idtyp}, and from Microsoft Entra it also carries no delegated
 * {@code scp}. A refused issuer or audience is described with what the token says and the setting it was held against,
 * which reaches the log and never the answer.
 */
@Configuration(proxyBeanMethods = false)
public class SelfHostedSecurityConfig {

    private static final Duration ISSUER_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration ISSUER_CALL_TIMEOUT = Duration.ofSeconds(10);

    private static final OAuth2Error ISSUED_TO_AN_APPLICATION = invalidToken(
            "The token was issued to an application, not to a person: its idtyp is app.");

    private static final OAuth2Error NO_DELEGATED_SCOPE = invalidToken(
            "The token was issued to an application, not to a person: an Entra token with no delegated scp.");

    @Bean
    IssuerKeys issuerKeys(SelfHostedProperties properties, Clock clock) {

        return new IssuerKeys(properties.oidc().issuerUri(), new RestTemplate(issuerRequests()), clock,
                IssuerKeys.Lifetimes.STANDARD);
    }

    @Bean
    JwtDecoder jwtDecoder(IssuerKeys issuerKeys, SelfHostedProperties properties, Clock clock) {

        var decoder = NimbusJwtDecoder.withJwkSource(issuerKeys)
                .jwsAlgorithms(algorithms -> algorithms.addAll(EnumSet.allOf(SignatureAlgorithm.class))).build();
        decoder.setJwtValidator(validator(properties.oidc(), clock));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validator(SelfHostedProperties.Oidc oidc, Clock clock) {

        var timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        var accessTokenTypes = new JwtTypeValidator("JWT", "at+jwt", "application/at+jwt");
        accessTokenTypes.setAllowEmpty(true);
        return JwtValidators.createDefaultWithValidators(List.of(timestamps, accessTokenTypes, issuer(oidc),
                audience(oidc), person(EntraIssuer.tenantOf(oidc.issuerUri()).isPresent())));
    }

    private static OAuth2TokenValidator<Jwt> issuer(SelfHostedProperties.Oidc oidc) {

        var issuer = new JwtIssuerValidator(oidc.issuerUri());
        return token -> issuer.validate(token).hasErrors()
                ? OAuth2TokenValidatorResult.failure(invalidToken("The token's issuer " + token.getClaimAsString("iss")
                        + " is not the configured AXONIA_SELFHOSTED_OIDC_ISSUER_URI " + oidc.issuerUri() + "."))
                : OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidator<Jwt> audience(SelfHostedProperties.Oidc oidc) {

        return token -> {
            var audience = Objects.requireNonNullElse(token.getAudience(), List.<String>of());
            return audience.contains(oidc.audience())
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(invalidToken("The token's audience " + audience
                            + " does not include the configured AXONIA_SELFHOSTED_OIDC_AUDIENCE " + oidc.audience()
                            + "."));
        };
    }

    private static OAuth2TokenValidator<Jwt> person(boolean entra) {

        return token -> {
            if ("app".equals(token.getClaimAsString("idtyp"))) {
                return OAuth2TokenValidatorResult.failure(ISSUED_TO_AN_APPLICATION);
            }
            return entra && !token.hasClaim("scp")
                    ? OAuth2TokenValidatorResult.failure(NO_DELEGATED_SCOPE)
                    : OAuth2TokenValidatorResult.success();
        };
    }

    private static OAuth2Error invalidToken(String description) {

        return new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null);
    }

    /**
     * The JDK client's read timeout bounds the whole call, its body included, not each read. It speaks HTTP/1.1 only,
     * so an issuer over http is never offered an h2c upgrade.
     */
    public static JdkClientHttpRequestFactory issuerRequests() {

        var requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(ISSUER_CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build());
        requests.setReadTimeout(ISSUER_CALL_TIMEOUT);
        return requests;
    }
}
