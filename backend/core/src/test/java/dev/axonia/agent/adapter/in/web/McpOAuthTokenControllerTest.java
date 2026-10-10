package dev.axonia.agent.adapter.in.web;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.FakeRateLimitStore;
import dev.axonia.agent.application.McpEndpoints;
import dev.axonia.agent.configuration.AgentSecurityConfig;
import dev.axonia.agent.configuration.McpOAuthConfig;
import dev.axonia.agent.configuration.McpOAuthProperties;
import dev.axonia.identity.api.connection.AgentConnections;
import dev.axonia.identity.api.connection.IssuedTokens;
import dev.axonia.identity.api.connection.RedeemCode;
import dev.axonia.identity.api.connection.RefreshConnection;
import dev.axonia.shared.api.ratelimit.Budget;
import dev.axonia.shared.api.ratelimit.RateLimiter;
import dev.axonia.shared.api.web.ClientAddress;
import dev.axonia.shared.api.web.PublicUrls;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;

/**
 * The slice context, and so the token budget, is shared by every test here. Each test therefore calls from its own
 * edge-stamped address, which keeps the budget small enough for the rate-limit test to spend.
 */
@CoreWebMvcTest(controllers = McpOAuthTokenController.class)
@Import({McpEndpoints.class, McpOAuthConfig.PublicMcpOAuthSecurityConfig.class,
        McpOAuthTokenControllerTest.Limits.class})
class McpOAuthTokenControllerTest extends ControllerTestSupport {

    private static final IssuedTokens ISSUED = new IssuedTokens("axa_access", "axr_refresh", Duration.ofHours(1),
            Set.of("read"));

    private static final String CLIENT = "https://claude.ai/c";

    private static final AtomicInteger ADDRESSES = new AtomicInteger();

    @MockitoBean
    AgentConnections connections;

    @MockitoBean
    PublicUrls publicUrl;

    private String address;

    @TestConfiguration(proxyBeanMethods = false)
    static class Limits {

        @Bean
        AgentSecurityConfig.McpRateLimiters limiters() {

            var store = new FakeRateLimitStore();
            var meters = new SimpleMeterRegistry();
            return new AgentSecurityConfig.McpRateLimiters(
                    new RateLimiter("c", new Budget(100, Duration.ofMinutes(1)), store, meters),
                    new RateLimiter("f", new Budget(100, Duration.ofMinutes(1)), store, meters),
                    new RateLimiter("t", new Budget(3, Duration.ofMinutes(1)), store, meters),
                    new RateLimiter("consent", new Budget(100, Duration.ofMinutes(1)), store, meters));
        }

        @Bean
        McpOAuthProperties mcpOAuthProperties() {
            return new McpOAuthProperties(Set.of("claude.ai"));
        }
    }

    @BeforeEach
    void setUp() {

        when(publicUrl.publicBaseUrl()).thenReturn("https://axonia.test");
        address = "198.51.100." + ADDRESSES.incrementAndGet();
    }

    @Test
    void authorizationCode_issuesAPairWithNoStore() {

        // given
        var command = new RedeemCode("axc_1", "https://claude.ai/c", "http://localhost:1/callback", "v".repeat(43));
        when(connections.redeem(command)).thenReturn(Optional.of(ISSUED));

        // when
        var actual = form("/oauth/token").param("grant_type", "authorization_code").param("code", "axc_1")
                .param("client_id", "https://claude.ai/c").param("redirect_uri", "http://localhost:1/callback")
                .param("code_verifier", "v".repeat(43)).param("resource", "https://axonia.test/mcp");

        // then
        actual.assertThat().hasStatusOk().hasHeader("Cache-Control", "no-store").hasHeader("Pragma", "no-cache")
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.access_token").isEqualTo("axa_access");
                    json.assertThat().extractingPath("$.token_type").isEqualTo("Bearer");
                    json.assertThat().extractingPath("$.expires_in").isEqualTo(3600);
                    json.assertThat().extractingPath("$.refresh_token").isEqualTo("axr_refresh");
                    json.assertThat().extractingPath("$.scope").isEqualTo("read");
                });
    }

    @Test
    void authorizationCode_answersInvalidGrantWhenRefused() {

        when(connections.redeem(any())).thenReturn(Optional.empty());

        var actual = form("/oauth/token").param("grant_type", "authorization_code").param("code", "axc_1")
                .param("client_id", CLIENT).param("redirect_uri", "r").param("code_verifier", "v");

        actual.assertThat().hasStatus(400).hasHeader("Cache-Control", "no-store").bodyJson().extractingPath("$.error")
                .isEqualTo("invalid_grant");
    }

    @Test
    void authorizationCode_answersInvalidRequestWhenAParameterIsMissingBlankOrRepeated() {

        form("/oauth/token").param("grant_type", "authorization_code").param("code", "axc_1").assertThat()
                .hasStatus(400).bodyJson().extractingPath("$.error").isEqualTo("invalid_request");
        form("/oauth/token").param("grant_type", "authorization_code").param("code", " ").param("client_id", "c")
                .param("redirect_uri", "r").param("code_verifier", "v").assertThat().hasStatus(400).bodyJson()
                .extractingPath("$.error").isEqualTo("invalid_request");
        form("/oauth/token").param("grant_type", "authorization_code").param("code", "axc_1", "axc_2")
                .param("client_id", "c").param("redirect_uri", "r").param("code_verifier", "v").assertThat()
                .hasStatus(400).bodyJson().extractingPath("$.error").isEqualTo("invalid_request");
        verify(connections, never()).redeem(any());
    }

    @Test
    void anyGrant_answersInvalidTargetForAnotherResource() {

        var actual = form("/oauth/token").param("grant_type", "refresh_token").param("refresh_token", "axr_1")
                .param("client_id", "c").param("resource", "https://elsewhere.test/mcp");

        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.error").isEqualTo("invalid_target");
    }

    @Test
    void refreshToken_rotates() {

        when(connections.refresh(new RefreshConnection("axr_1", CLIENT))).thenReturn(Optional.of(ISSUED));

        var actual = form("/oauth/token").param("grant_type", "refresh_token").param("refresh_token", "axr_1")
                .param("client_id", CLIENT);

        actual.assertThat().hasStatusOk().bodyJson().extractingPath("$.refresh_token").isEqualTo("axr_refresh");
    }

    @Test
    void refreshToken_answersInvalidRequestWithoutAClientId() {

        var actual = form("/oauth/token").param("grant_type", "refresh_token").param("refresh_token", "axr_1");

        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.error").isEqualTo("invalid_request");
        verify(connections, never()).refresh(any());
    }

    @Test
    void unknownGrant_isUnsupported() {

        var actual = form("/oauth/token").param("grant_type", "client_credentials");

        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.error").isEqualTo("unsupported_grant_type");
    }

    @Test
    void token_isRateLimitedPerAddress() {

        when(connections.refresh(any())).thenReturn(Optional.empty());
        for (var i = 0; i < 3; i++) {
            form("/oauth/token").param("grant_type", "refresh_token").param("refresh_token", "axr_x")
                    .param("client_id", CLIENT).assertThat().hasStatus(400);
        }

        form("/oauth/token").param("grant_type", "refresh_token").param("refresh_token", "axr_x")
                .param("client_id", CLIENT).assertThat().hasStatus(429).containsHeader("Retry-After")
                .hasHeader("Cache-Control", "no-store").bodyText().isEmpty();
    }

    @Test
    void revoke_answers200ForAnyToken() {

        form("/oauth/revoke").param("token", "axr_1").param("token_type_hint", "refresh_token").assertThat()
                .hasStatusOk().hasHeader("Cache-Control", "no-store");

        verify(connections).revokeToken("axr_1");
    }

    @Test
    void revoke_answersInvalidRequestWithoutAToken() {

        form("/oauth/revoke").assertThat().hasStatus(400).bodyJson().extractingPath("$.error")
                .isEqualTo("invalid_request");
        form("/oauth/revoke").param("token", " ").assertThat().hasStatus(400).bodyJson().extractingPath("$.error")
                .isEqualTo("invalid_request");
        verify(connections, never()).revokeToken(any());
    }

    // Parameters in the URL, a stray bearer, and a client no longer trusted

    @Test
    void token_refusesParametersInTheQueryString() {

        var actual = mvc.post().uri("/oauth/token?grant_type=refresh_token&refresh_token=axr_inurl&client_id=" + CLIENT)
                .with(anonymous()).header(ClientAddress.HEADER, address)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);

        actual.assertThat().hasStatus(400).hasHeader("Cache-Control", "no-store").bodyJson().extractingPath("$.error")
                .isEqualTo("invalid_request");
        verify(connections, never()).refresh(any());
    }

    @Test
    void revoke_refusesParametersInTheQueryString() {

        var actual = mvc.post().uri("/oauth/revoke?token=axr_inurl").with(anonymous())
                .header(ClientAddress.HEADER, address).contentType(MediaType.APPLICATION_FORM_URLENCODED);

        actual.assertThat().hasStatus(400).hasHeader("Cache-Control", "no-store").bodyJson().extractingPath("$.error")
                .isEqualTo("invalid_request");
        verify(connections, never()).revokeToken(any());
    }

    @Test
    void token_reachesTheEndpointDespiteAStrayBearer() {

        when(connections.refresh(new RefreshConnection("axr_1", CLIENT))).thenReturn(Optional.of(ISSUED));

        var actual = form("/oauth/token").header("Authorization", "Bearer x").param("grant_type", "refresh_token")
                .param("refresh_token", "axr_1").param("client_id", CLIENT);

        actual.assertThat().hasStatusOk().bodyJson().extractingPath("$.refresh_token").isEqualTo("axr_refresh");
    }

    @Test
    void revoke_reachesTheEndpointDespiteAStrayBearer() {

        form("/oauth/revoke").header("Authorization", "Bearer x").param("token", "axr_1").assertThat().hasStatusOk();

        verify(connections).revokeToken("axr_1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example/c", "c", "not a url"})
    void authorizationCode_answersInvalidGrantForAClientNoLongerTrusted(String clientId) {

        var actual = form("/oauth/token").param("grant_type", "authorization_code").param("code", "axc_1")
                .param("client_id", clientId).param("redirect_uri", "r").param("code_verifier", "v");

        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.error").isEqualTo("invalid_grant");
        verify(connections, never()).redeem(any());
    }

    @Test
    void refreshToken_answersInvalidGrantForAClientNoLongerTrusted() {

        var actual = form("/oauth/token").param("grant_type", "refresh_token").param("refresh_token", "axr_1")
                .param("client_id", "https://evil.example/c");

        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.error").isEqualTo("invalid_grant");
        verify(connections, never()).refresh(any());
    }

    private MockMvcRequestBuilder form(String path) {

        return mvc.post().uri(path).with(anonymous()).header(ClientAddress.HEADER, address)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);
    }
}
