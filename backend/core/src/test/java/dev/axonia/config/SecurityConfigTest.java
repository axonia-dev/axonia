package dev.axonia.config;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import dev.axonia.CoreWebMvcTest;
import dev.axonia.SteppableClock;
import dev.axonia.community.CommunityController;
import dev.axonia.community.CommunityPolicyService;
import dev.axonia.community.CommunityService;
import dev.axonia.community.LookAndFeelService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@CoreWebMvcTest(controllers = CommunityController.class)
@ActiveProfiles("DEV")
@Import({SecurityConfig.class, SecurityConfigTest.StubBearerProviderConfig.class,
        SecurityConfigTest.SteppedClockConfig.class})
@ExtendWith(OutputCaptureExtension.class)
class SecurityConfigTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CommunityService communityService;

    @MockitoBean
    private CommunityPolicyService communityPolicyService;

    @MockitoBean
    private LookAndFeelService lookAndFeelService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private SteppableClock clock;

    @Test
    void anonymousRequestIsRejectedWithAProblemDetail() {

        // when
        var actual = mvc.get().uri("/axonia/communities");

        // then
        actual.assertThat().hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        actual.assertThat().bodyJson().extractingPath("$.status").isEqualTo(401);
        actual.assertThat().bodyJson().extractingPath("$.detail").isEqualTo("Authentication required");
    }

    @Test
    void authenticatedRequestReachesTheController() {

        // when
        var actual = mvc.get().uri("/axonia/communities")
                .with(jwt().jwt(token -> token.subject("alice").claim("org", "default")));

        // then
        actual.assertThat().hasStatus2xxSuccessful();
    }

    @Test
    void ordinaryBearerIsAuthenticatedByTheJwtDecoder() {

        // given
        when(jwtDecoder.decode("idp-token")).thenReturn(synthesizedJwt("alice", "default"));

        // when
        var actual = mvc.get().uri("/axonia/communities").header(HttpHeaders.AUTHORIZATION, "Bearer idp-token");

        // then
        actual.assertThat().hasStatus2xxSuccessful();
    }

    @Test
    void aContributedBearerIsAuthenticatedByItsProvider() {

        // when
        var actual = mvc.get().uri("/axonia/communities").header(HttpHeaders.AUTHORIZATION, "Bearer axi_live");

        // then
        actual.assertThat().hasStatus2xxSuccessful();
    }

    @Test
    void aContributedBearerRejectionCarriesItsCode() {

        // when
        var actual = mvc.get().uri("/axonia/communities").header(HttpHeaders.AUTHORIZATION, "Bearer axi_revoked");

        // then
        actual.assertThat().hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        actual.assertThat().bodyJson().extractingPath("$.code").isEqualTo("session_revoked");
    }

    @Test
    void aBearerWhoseIssuerCannotBeReachedIsAnsweredAsUnavailable() {

        // given
        when(jwtDecoder.decode("idp-token"))
                .thenThrow(new JwtDecoderInitializationException("discovery failed", new IllegalStateException()));

        // when
        var actual = mvc.get().uri("/axonia/communities").header(HttpHeaders.AUTHORIZATION, "Bearer idp-token")
                .exchange();

        // then
        assertThat(actual).hasStatus(HttpStatus.SERVICE_UNAVAILABLE).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(actual).headers().hasSingleValue(HttpHeaders.RETRY_AFTER, "10");
        assertThat(actual).bodyJson().extractingPath("$.status").isEqualTo(503);
        assertThat(actual).bodyJson().extractingPath("$.code").isEqualTo("issuer_unavailable");
        assertThat(actual).bodyJson().extractingPath("$.detail").isEqualTo("The identity provider cannot be reached");
    }

    @Test
    void aRefusedBearerIsAnsweredAsBeforeAndItsReasonIsSaidOnlyInTheLog(CapturedOutput output) throws Exception {

        // given
        var reason = "The token's audience [api://another-application] does not include the configured audience";
        when(jwtDecoder.decode("idp-token"))
                .thenThrow(new JwtValidationException("An error occurred while attempting to decode the Jwt: " + reason,
                        List.of(new OAuth2Error("invalid_token", reason, null))));

        // when
        var actual = mvc.get().uri("/axonia/communities").header(HttpHeaders.AUTHORIZATION, "Bearer idp-token")
                .exchange();

        // then
        assertThat(actual).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(actual).bodyJson().extractingPath("$.detail").isEqualTo("Authentication required");
        assertThat(actual).bodyJson().extractingPath("$.code").isEqualTo("invalid_token");
        assertThat(actual.getResponse().getContentAsString()).doesNotContain("audience");
        assertThat(actual.getResponse().getHeaderNames()).doesNotContain(HttpHeaders.WWW_AUTHENTICATE);
        assertThat(output.getOut()).contains("Refused a bearer token: " + reason + " (iss=unreadable, aud=unreadable)");
    }

    @Test
    void refusalsAreThrottledOnTheApplicationsClock(CapturedOutput output) {

        // given
        var reason = "Signed JWT rejected: a reason only this test gives";
        when(jwtDecoder.decode("idp-token")).thenThrow(new BadJwtException(reason));

        // when - the same reason a minute and a second apart on the application's clock, at once on the wall's
        mvc.get().uri("/axonia/communities").header(HttpHeaders.AUTHORIZATION, "Bearer idp-token").exchange();
        clock.advance(Duration.ofSeconds(61));
        mvc.get().uri("/axonia/communities").header(HttpHeaders.AUTHORIZATION, "Bearer idp-token").exchange();

        // then
        assertThat(output.getOut().split("Refused a bearer token: " + reason, -1)).hasSize(3);
    }

    private static Jwt synthesizedJwt(String subject, String organizationId) {

        return Jwt.withTokenValue("token").header("alg", "none").subject(subject).claim("org", organizationId).build();
    }

    /** The application's clock, which a web slice of this chain alone never registers. */
    @TestConfiguration(proxyBeanMethods = false)
    static class SteppedClockConfig {

        @Bean
        SteppableClock clock() {

            return new SteppableClock(Instant.parse("2026-10-03T09:00:00Z"));
        }
    }

    /** Stands in for a module's bearer provider, which a web slice of this chain alone never registers. */
    @TestConfiguration(proxyBeanMethods = false)
    static class StubBearerProviderConfig {

        private final AuthenticationProvider contributedProvider = new AuthenticationProvider() {

            @Override
            public Authentication authenticate(Authentication authentication) {
                var token = ((BearerTokenAuthenticationToken) authentication).getToken();
                if ("axi_revoked".equals(token)) {
                    throw new OAuth2AuthenticationException(new OAuth2Error("session_revoked"));
                }
                return new JwtAuthenticationToken(synthesizedJwt("u-target", "acme"), List.of());
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return BearerTokenAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };

        @Bean
        BearerCredentialProvider axiBearerCredentialProvider() {

            return new BearerCredentialProvider() {

                @Override
                public String prefix() {

                    return "axi_";
                }

                @Override
                public AuthenticationProvider provider() {

                    return contributedProvider;
                }
            };
        }
    }
}
