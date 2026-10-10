package dev.axonia.shared.adapter.in.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import dev.axonia.SteppableClock;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class BearerRefusalsTest {

    private static final String LINE = "Refused a bearer token: ";

    private static final Instant PAST = Instant.now().minus(Duration.ofHours(2));

    private final SteppableClock clock = new SteppableClock(Instant.parse("2026-10-03T09:00:00Z"));

    private final BearerRefusals refusals = new BearerRefusals(clock);

    @Test
    void aRefusalIsLoggedWithItsReasonAndTheIssuerAndAudienceTheTokenClaims(CapturedOutput output) {

        // given
        var bearer = bearer(new JWTClaimsSet.Builder().issuer("https://login.example.com/tenant/v2.0")
                .audience("api://another-application").build());

        // when
        refusals.refused(bearer, invalid("The token is not for this installation's audience."));

        // then
        assertThat(output.getOut()).contains(LINE + "The token is not for this installation's audience. "
                + "(iss=https://login.example.com/tenant/v2.0, aud=[api://another-application])");
    }

    @Test
    void noPartOfTheTokenIsLogged(CapturedOutput output) {

        // given
        var bearer = bearer(new JWTClaimsSet.Builder().issuer("https://login.example.com").audience("api://axonia")
                .subject("alice").build());

        // when
        refusals.refused(bearer, invalid("Signed JWT rejected: Invalid signature"));

        // then
        assertThat(output.getOut()).contains(LINE);
        assertThat(bearer.getToken().split("\\.")).allSatisfy(part -> assertThat(output.getOut()).doesNotContain(part));
    }

    @Test
    void everyDescriptionOfAFailedValidationIsTheReason(CapturedOutput output) {

        // given
        var failed = new JwtValidationException("An error occurred while attempting to decode the Jwt: first",
                List.of(new OAuth2Error("invalid_token", "first", null), new OAuth2Error("invalid_token"),
                        new OAuth2Error("invalid_token", "second", null)));

        // when
        refusals.refused(bearer(new JWTClaimsSet.Builder().build()), new InvalidBearerTokenException("first", failed));

        // then
        assertThat(output.getOut()).contains(LINE + "first; second (iss=none, aud=[])");
    }

    @Test
    void aFailedValidationWithoutADescriptionIsReadFromItsMessage(CapturedOutput output) {

        // given
        var failed = new JwtValidationException("Unable to validate Jwt", List.of(new OAuth2Error("invalid_token")));

        // when
        refusals.refused(bearer(new JWTClaimsSet.Builder().build()),
                new InvalidBearerTokenException("Unable to validate Jwt", failed));

        // then
        assertThat(output.getOut()).contains(LINE + "Unable to validate Jwt (");
    }

    @Test
    void aReasonIsLoggedOnceAMinute(CapturedOutput output) {

        // given
        var bearer = bearer(new JWTClaimsSet.Builder().build());

        // when
        refusals.refused(bearer, invalid("Signed JWT rejected: Invalid signature"));
        refusals.refused(bearer, invalid("Signed JWT rejected: Invalid signature"));
        clock.advance(Duration.ofSeconds(59));
        refusals.refused(bearer, invalid("Signed JWT rejected: Invalid signature"));
        var withinTheMinute = lines(output);
        clock.advance(Duration.ofSeconds(1));
        refusals.refused(bearer, invalid("Signed JWT rejected: Invalid signature"));

        // then
        assertThat(withinTheMinute).isEqualTo(1);
        assertThat(lines(output)).isEqualTo(2);
    }

    @Test
    void numbersInAReasonDoNotMakeItAnotherReason(CapturedOutput output) {

        // given
        var bearer = bearer(new JWTClaimsSet.Builder().build());

        // when
        refusals.refused(bearer, invalid("Jwt used before 2026-10-03T09:10:01Z"));
        refusals.refused(bearer, invalid("Jwt used before 2026-10-03T09:10:02Z"));
        refusals.refused(bearer, invalid("Signed JWT rejected: Invalid signature"));

        // then
        assertThat(lines(output)).isEqualTo(2);
        assertThat(output.getOut()).contains(LINE + "Jwt used before 2026-10-03T09:10:01Z").doesNotContain("09:10:02");
    }

    @Test
    void aTokenRefusedOnlyBecauseItExpiredIsNotSaid(CapturedOutput output) {

        // given - Spring's own description of an expired token
        var refusal = refusal(new JwtTimestampValidator().validate(jwt(builder -> builder.expiresAt(PAST))));

        // when
        refusals.refused(bearer(new JWTClaimsSet.Builder().issuer("https://login.example.com").build()), refusal);

        // then
        assertThat(refusal.getCause()).hasMessageContaining("Jwt expired at");
        assertThat(output.getOut()).doesNotContain(LINE).doesNotContain("WARN");
    }

    @Test
    void anExpiredTokenRefusedForAnythingElseTooIsSaid(CapturedOutput output) {

        // given - expired, and for another audience
        var expired = new JwtTimestampValidator().validate(jwt(builder -> builder.expiresAt(PAST))).getErrors();
        var errors = new ArrayList<>(expired);
        errors.add(new OAuth2Error("invalid_token",
                "The token's audience [api://another-application] does not " + "include the configured audience",
                null));
        var refusal = new InvalidBearerTokenException("Invalid token", new JwtValidationException("Invalid", errors));

        // when
        refusals.refused(bearer(new JWTClaimsSet.Builder().build()), refusal);

        // then
        assertThat(output.getOut()).contains(LINE + "Jwt expired at ").contains("WARN").contains(
                "; The token's audience [api://another-application] does not include the configured audience");
    }

    @Test
    void aTokenUsedBeforeItsTimeIsSaid(CapturedOutput output) {

        // given - an issuer's clock ahead of the installation's says something is misconfigured
        var refusal = refusal(new JwtTimestampValidator()
                .validate(jwt(builder -> builder.notBefore(Instant.now().plus(Duration.ofHours(1))))));

        // when
        refusals.refused(bearer(new JWTClaimsSet.Builder().build()), refusal);

        // then
        assertThat(output.getOut()).contains("WARN").contains(LINE + "Jwt used before ");
    }

    @Test
    void atMostThirtyTwoReasonsAreLoggedAMinuteAndBeingFullIsSaidOnce(CapturedOutput output) {

        // given
        var bearer = bearer(new JWTClaimsSet.Builder().build());

        // when
        IntStream.range(0, 40).forEach(i -> refusals.refused(bearer, invalid("reason " + "x".repeat(i))));
        var withinTheMinute = lines(output);
        clock.advance(BearerRefusals.WINDOW);
        refusals.refused(bearer, invalid("a new reason"));
        IntStream.range(0, 40).forEach(i -> refusals.refused(bearer, invalid("reason " + "y".repeat(i))));

        // then
        assertThat(withinTheMinute).isEqualTo(BearerRefusals.REASONS);
        assertThat(lines(output)).isEqualTo(2 * BearerRefusals.REASONS);
        assertThat(output.getOut()).contains(LINE + "a new reason")
                .containsOnlyOnce(LINE + "reason " + "x".repeat(BearerRefusals.REASONS - 1) + " (")
                .doesNotContain(LINE + "reason " + "x".repeat(BearerRefusals.REASONS) + " (");
        assertThat(output.getOut().split("Refused bearer tokens for 32 reasons within a minute", -1)).hasSize(3);
    }

    @Test
    void aTokenThatCannotBeReadIsLoggedAsUnreadable(CapturedOutput output) {

        // when
        refusals.refused(new BearerTokenAuthenticationToken("not-a-jwt"), invalid("Malformed token"));
        refusals.refused(new TestingAuthenticationToken("someone", null), invalid("Not a bearer"));

        // then
        assertThat(output.getOut()).contains(LINE + "Malformed token (iss=unreadable, aud=unreadable)")
                .contains(LINE + "Not a bearer (iss=unreadable, aud=unreadable)");
    }

    @Test
    void whatATokenClaimsCannotForgeALogLineNorRunOn(CapturedOutput output) {

        // given
        var bearer = bearer(
                new JWTClaimsSet.Builder().issuer("https://x\nWARN forged line\r").audience("a".repeat(300)).build());

        // when
        refusals.refused(bearer,
                new InvalidBearerTokenException("Invalid token", new JwtValidationException("Invalid token",
                        List.of(new OAuth2Error("invalid_token", "Signed JWT rejected:\tInvalid signature", null)))));

        // then
        assertThat(output.getOut())
                .contains(LINE + "Signed JWT rejected:?Invalid signature (iss=https://x?WARN forged line?, aud=["
                        + "a".repeat(199) + "...)")
                .doesNotContain("\nWARN forged line");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u0085", "\u2028", "\u2029", "\u202E", "\u200B", "\u0000", "\u009B", "\uD800"})
    void noUnicodeControlSeparatorOrFormatCharacterIsLeftInAValue(String unprintable) {

        // when
        var actual = BearerRefusals.printable("https://x" + unprintable + "WARN forged line");

        // then
        assertThat(actual).isEqualTo("https://x?WARN forged line");
    }

    @Test
    void aLongValueIsCutAfterTwoHundredCharactersNeverInsideOne() {

        // when
        var actual = BearerRefusals.printable("\uD83D\uDE00".repeat(300));

        // then
        assertThat(actual).isEqualTo("\uD83D\uDE00".repeat(200) + "...");
    }

    private int lines(CapturedOutput output) {

        return output.getOut().split(LINE, -1).length - 1;
    }

    private static InvalidBearerTokenException refusal(OAuth2TokenValidatorResult result) {

        return new InvalidBearerTokenException("Invalid token",
                new JwtValidationException(result.getErrors().iterator().next().getDescription(), result.getErrors()));
    }

    private static Jwt jwt(UnaryOperator<Jwt.Builder> claims) {

        return claims.apply(Jwt.withTokenValue("t").header("alg", "RS256").subject("alice")
                .issuedAt(Instant.now().minus(Duration.ofHours(3))).expiresAt(Instant.now().plus(Duration.ofHours(1))))
                .build();
    }

    private static InvalidBearerTokenException invalid(String reason) {

        return new InvalidBearerTokenException(reason);
    }

    private static BearerTokenAuthenticationToken bearer(JWTClaimsSet claims) {

        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner("a-secret-of-at-least-thirty-two-bytes!"));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return new BearerTokenAuthenticationToken(jwt.serialize());
    }
}
