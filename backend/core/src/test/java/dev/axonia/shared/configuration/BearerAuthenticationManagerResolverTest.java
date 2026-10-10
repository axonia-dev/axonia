package dev.axonia.shared.configuration;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.convert.converter.Converter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.nimbusds.jose.RemoteKeySourceException;

import dev.axonia.shared.adapter.in.security.BearerRefusals;
import dev.axonia.shared.adapter.in.security.IssuerUnavailableException;
import dev.axonia.shared.api.security.BearerCredentialProvider;
import dev.axonia.shared.api.security.CurrentToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class BearerAuthenticationManagerResolverTest {

    private final JwtDecoder decoder = mock(JwtDecoder.class);

    private final BearerRefusals refusals = new BearerRefusals(Clock.systemUTC());

    @Test
    void aClaimedPrefixGoesToItsProvider() {

        var claimed = provider(new TestingAuthenticationToken("claimed", null));
        var resolver = SecurityConfig.resolverFor(decoder, List.of(bearer("axi_", claimed)), null, refusals);

        var manager = resolver.resolve(request("Bearer axi_abc"));

        assertThat(manager.authenticate(new TestingAuthenticationToken("x", null)).getName()).isEqualTo("claimed");
    }

    @Test
    void anUnclaimedBearerGoesToTheJwtManager() {

        var claimed = provider(new TestingAuthenticationToken("claimed", null));
        var resolver = SecurityConfig.resolverFor(decoder, List.of(bearer("axi_", claimed)), null, refusals);

        assertThat(resolver.resolve(request("Bearer eyJhbGciOi")))
                .isNotSameAs(resolver.resolve(request("Bearer axi_abc")));
    }

    @Test
    void aRegisteredConverterShapesTheJwtAuthentication() {

        // given
        var jwt = Jwt.withTokenValue("idp-token").header("alg", "none").subject("alice").build();
        when(decoder.decode("idp-token")).thenReturn(jwt);
        Converter<Jwt, AbstractAuthenticationToken> converter = token -> new TestingAuthenticationToken(
                "converted-" + token.getSubject(), null);
        var resolver = SecurityConfig.resolverFor(decoder, List.of(), converter, refusals);

        // when
        var manager = resolver.resolve(request("Bearer idp-token"));
        var actual = manager.authenticate(new BearerTokenAuthenticationToken("idp-token"));

        // then - the authentication came from the registered converter, not the default one
        assertThat(actual.getName()).isEqualTo("converted-alice");
    }

    @Test
    void withoutARegisteredConverterTheJwtIsConvertedAsTheResourceServerWould() {

        // given
        var jwt = Jwt.withTokenValue("idp-token").header("alg", "none").subject("alice").claim("scope", "openid email")
                .build();
        when(decoder.decode("idp-token")).thenReturn(jwt);
        var manager = SecurityConfig.resolverFor(decoder, List.of(), null, refusals)
                .resolve(request("Bearer idp-token"));

        // when
        var actual = manager.authenticate(new BearerTokenAuthenticationToken("idp-token"));

        // then
        assertThat(actual).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(((JwtAuthenticationToken) actual).getToken()).isSameAs(jwt);
        assertThat(actual.getName()).isEqualTo("alice");
        assertThat(actual.getAuthorities()).extracting(Object::toString).contains("SCOPE_openid", "SCOPE_email");
    }

    @Test
    void aJwtThatActsForSomebodyIsRefusedBeforeTheRegisteredConverterSeesIt(CapturedOutput output) {

        // given
        when(decoder.decode("idp-token")).thenReturn(actingToken());
        var converted = new AtomicBoolean();
        Converter<Jwt, AbstractAuthenticationToken> converter = token -> {
            converted.set(true);
            return new TestingAuthenticationToken(token.getSubject(), null);
        };
        var manager = SecurityConfig.resolverFor(decoder, List.of(), converter, refusals)
                .resolve(request("Bearer idp-token"));

        // when
        var actual = catchThrowable(() -> manager.authenticate(new BearerTokenAuthenticationToken("idp-token")));

        // then
        assertThat(actual).isInstanceOf(InvalidBearerTokenException.class)
                .hasMessage("Only a person's own sign-in is accepted, and this token acts for somebody.");
        assertThat(converted).isFalse();
        assertThat(output.getOut()).contains(
                "Refused a bearer token: Only a person's own sign-in is accepted, and this token acts for somebody.");
    }

    @Test
    void aJwtThatActsForSomebodyIsRefusedWithoutARegisteredConverter() {

        // given
        when(decoder.decode("idp-token")).thenReturn(actingToken());
        var manager = SecurityConfig.resolverFor(decoder, List.of(), null, refusals)
                .resolve(request("Bearer idp-token"));

        // when / then
        assertThatThrownBy(() -> manager.authenticate(new BearerTokenAuthenticationToken("idp-token")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void aRefusedJwtIsSaidInTheLogAndRefusedAsBefore(CapturedOutput output) {

        // given
        var refusal = new BadJwtException("Signed JWT rejected: Invalid signature");
        when(decoder.decode("idp-token")).thenThrow(refusal);
        var manager = SecurityConfig.resolverFor(decoder, List.of(), null, refusals)
                .resolve(request("Bearer idp-token"));

        // when
        var actual = catchThrowable(() -> manager.authenticate(new BearerTokenAuthenticationToken("idp-token")));

        // then
        assertThat(actual).isInstanceOf(InvalidBearerTokenException.class).hasCause(refusal);
        assertThat(output.getOut()).contains(
                "Refused a bearer token: Signed JWT rejected: Invalid signature (iss=unreadable, aud=unreadable)");
    }

    @Test
    void anIssuerThatCannotBeReachedIsTheIssuerBeingUnavailable(CapturedOutput output) {

        // given
        var unreachable = new JwtDecoderInitializationException("discovery failed", new IllegalStateException());
        when(decoder.decode("idp-token")).thenThrow(unreachable);
        var manager = SecurityConfig.resolverFor(decoder, List.of(), null, refusals)
                .resolve(request("Bearer idp-token"));

        // when / then - an AuthenticationException the entry point answers, not a raw exception escaping as a 500
        assertThatThrownBy(() -> manager.authenticate(new BearerTokenAuthenticationToken("idp-token")))
                .isInstanceOf(IssuerUnavailableException.class).hasCause(unreachable);
        assertThat(output.getOut()).doesNotContain("Refused a bearer token");
    }

    @Test
    void keysThatCannotBeFetchedAreTheIssuerBeingUnavailable() {

        // given - a built decoder that must refresh the issuer's keys, which cannot be reached
        when(decoder.decode("idp-token")).thenThrow(new JwtException("Read timed out",
                new RemoteKeySourceException("Read timed out", new SocketTimeoutException("Read timed out"))));
        var manager = SecurityConfig.resolverFor(decoder, List.of(), null, refusals)
                .resolve(request("Bearer idp-token"));

        // when / then
        assertThatThrownBy(() -> manager.authenticate(new BearerTokenAuthenticationToken("idp-token")))
                .isInstanceOf(IssuerUnavailableException.class).rootCause().isInstanceOf(SocketTimeoutException.class);
    }

    @Test
    void aDecoderFailureThatIsNotTheIssuerIsLeftAsItIs(CapturedOutput output) {

        // given
        when(decoder.decode("idp-token")).thenThrow(new JwtException("An unexpected processing failure"));
        var manager = SecurityConfig.resolverFor(decoder, List.of(), null, refusals)
                .resolve(request("Bearer idp-token"));

        // when / then
        assertThatThrownBy(() -> manager.authenticate(new BearerTokenAuthenticationToken("idp-token")))
                .isInstanceOf(AuthenticationServiceException.class).hasMessage("An unexpected processing failure");
        assertThat(output.getOut()).doesNotContain("Refused a bearer token");
    }

    @Test
    void anUnexpectedFailureIsNotTheIssuerBeingUnavailable() {

        // given - a bug on the JWT path must stay a 500 with its stack trace
        var bug = new IllegalStateException("bug");
        when(decoder.decode("idp-token")).thenThrow(bug);
        var manager = SecurityConfig.resolverFor(decoder, List.of(), null, refusals)
                .resolve(request("Bearer idp-token"));

        // when / then
        assertThatThrownBy(() -> manager.authenticate(new BearerTokenAuthenticationToken("idp-token"))).isSameAs(bug);
    }

    @Test
    void aBlankPrefixFailsAtConstruction() {

        var claimed = bearer("  ", provider(new TestingAuthenticationToken("x", null)));

        assertThatThrownBy(() -> SecurityConfig.resolverFor(decoder, List.of(claimed), null, refusals))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("blank").hasMessageEndingWith("'  '");
    }

    @Test
    void aPrefixStartingWithAJwtHeaderFailsAtConstruction() {

        var claimed = bearer("eyJfoo_", provider(new TestingAuthenticationToken("x", null)));

        assertThatThrownBy(() -> SecurityConfig.resolverFor(decoder, List.of(claimed), null, refusals))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("eyJ");
    }

    @Test
    void aPrefixThatAJwtHeaderStartsWithFailsAtConstruction() {

        var claimed = bearer("ey", provider(new TestingAuthenticationToken("x", null)));

        assertThatThrownBy(() -> SecurityConfig.resolverFor(decoder, List.of(claimed), null, refusals))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("eyJ").hasMessageEndingWith("'ey'");
    }

    @Test
    void aDuplicatedPrefixFailsAtConstructionNamingIt() {

        var first = bearer("axi_", provider(new TestingAuthenticationToken("a", null)));
        var second = bearer("axi_", provider(new TestingAuthenticationToken("b", null)));

        assertThatThrownBy(() -> SecurityConfig.resolverFor(decoder, List.of(first, second), null, refusals))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bearer credential prefixes must not be prefixes of each other: 'axi_' and 'axi_'");
    }

    @Test
    void aPrefixOfAnotherPrefixFailsAtConstruction() {

        var shorter = bearer("axi_", provider(new TestingAuthenticationToken("a", null)));
        var longer = bearer("axi_extra", provider(new TestingAuthenticationToken("b", null)));

        assertThatThrownBy(() -> SecurityConfig.resolverFor(decoder, List.of(shorter, longer), null, refusals))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("axi_")
                .hasMessageContaining("axi_extra");
    }

    @Test
    void aLongerPrefixListedBeforeItsShorterPrefixFailsAtConstruction() {

        var longer = bearer("axi_extra", provider(new TestingAuthenticationToken("a", null)));
        var shorter = bearer("axi_", provider(new TestingAuthenticationToken("b", null)));

        assertThatThrownBy(() -> SecurityConfig.resolverFor(decoder, List.of(longer, shorter), null, refusals))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bearer credential prefixes must not be prefixes of each other: 'axi_extra' and 'axi_'");
    }

    @Test
    void unrelatedPrefixesAreAccepted() {

        var first = bearer("axi_", provider(new TestingAuthenticationToken("a", null)));
        var second = bearer("svc_", provider(new TestingAuthenticationToken("b", null)));

        assertThat(SecurityConfig.resolverFor(decoder, List.of(first, second), null, refusals)).isNotNull();
    }

    private static Jwt actingToken() {

        return Jwt.withTokenValue("idp-token").header("alg", "none").subject("alice")
                .claim(CurrentToken.ACTOR_CLAIM, Map.of("sub", "service-1")).build();
    }

    private static MockHttpServletRequest request(String authorization) {

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", authorization);
        return request;
    }

    private static AuthenticationProvider provider(TestingAuthenticationToken result) {

        return new AuthenticationProvider() {

            @Override
            public org.springframework.security.core.Authentication authenticate(
                    org.springframework.security.core.Authentication authentication) {

                return result;
            }

            @Override
            public boolean supports(Class<?> authentication) {

                return true;
            }
        };
    }

    private static BearerCredentialProvider bearer(String prefix, AuthenticationProvider provider) {

        return new BearerCredentialProvider() {

            @Override
            public String prefix() {

                return prefix;
            }

            @Override
            public AuthenticationProvider provider() {

                return provider;
            }
        };
    }
}
