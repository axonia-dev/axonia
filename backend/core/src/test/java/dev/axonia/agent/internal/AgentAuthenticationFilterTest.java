package dev.axonia.agent.internal;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

import dev.axonia.Budget;
import dev.axonia.ClientAddress;
import dev.axonia.FakeRateLimitStore;
import dev.axonia.PublicUrlProperties;
import dev.axonia.RateLimiter;
import dev.axonia.agent.AgentAccess;
import dev.axonia.agent.internal.AgentPrincipal.Credential;
import dev.axonia.identity.connection.AgentConnections;
import dev.axonia.identity.connection.ConnectionPrincipal;
import dev.axonia.identity.pat.MemberAccounts;
import dev.axonia.identity.pat.PersonalAccessTokens;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class AgentAuthenticationFilterTest {

    private static final String RESOURCE_METADATA = "resource_metadata=\"https://axonia.test/.well-known/oauth-protected-resource/mcp\"";

    @Mock
    AgentCredentials credentials;
    @Mock
    MemberAccounts members;
    @Mock
    AgentAccess access;
    @Mock
    FilterChain chain;
    @Mock
    PersonalAccessTokens personalTokens;
    @Mock
    AgentConnections connections;

    AgentAuthenticationFilter sut;

    private final FakeRateLimitStore store = new FakeRateLimitStore();
    private final McpEndpoints endpoints = new McpEndpoints(new PublicUrlProperties("https://axonia.test"));
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        // Lenient: most tests never reach the access check (they fail earlier, on the bearer/token/active-owner
        // gates), so a strict stub would flag it as unused there. Every test that DOES reach it gets an available
        // organization by default, and the tests for the refusal itself override this with a stricter stub.
        lenient().when(access.isAvailableTo(anyString(), anyString())).thenReturn(true);
        sut = filter(rateLimiter("mcp-calls", 1000), rateLimiter("mcp-auth-failure", 1000));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private AgentAuthenticationFilter filter(RateLimiter callLimiter, RateLimiter authFailureLimiter) {
        return filter(credentials, callLimiter, authFailureLimiter);
    }

    private AgentAuthenticationFilter filter(AgentCredentials agentCredentials, RateLimiter callLimiter,
            RateLimiter authFailureLimiter) {
        var limiters = new AgentSecurityConfig.McpRateLimiters(callLimiter, authFailureLimiter,
                rateLimiter("mcp-oauth-token", 1000), rateLimiter("mcp-consent", 1000));
        return new AgentAuthenticationFilter(
                new AgentAuthenticationFilter.Collaborators(agentCredentials, members, limiters, access, endpoints));
    }

    private RateLimiter rateLimiter(String name, int limit) {
        return new RateLimiter(name, new Budget(limit, Duration.ofMinutes(1)), store, new SimpleMeterRegistry());
    }

    /**
     * A one-capacity budget already spent for the token id under test, which is the "budget already gone" state these
     * tests need. {@code limit=0} is not an option: {@link Budget} refuses it, because a budget that can never allow
     * anything is a configuration mistake rather than a way to spell "always refuse". The refusal wait is
     * window/capacity, so 60 seconds, which is what the assertions below expect.
     */
    private RateLimiter spentCallLimiter() {
        var limiter = rateLimiter("mcp-calls", 1);
        limiter.tryConsume("token:7");
        return limiter;
    }

    private static AgentPrincipal personal(String userId, String organizationId, long tokenId) {
        return new AgentPrincipal(userId, organizationId, Set.of("read"), Credential.PERSONAL_TOKEN, tokenId);
    }

    private MockHttpServletRequest bearer(String token) {
        var req = new MockHttpServletRequest("POST", "/mcp");
        req.addHeader("Authorization", "Bearer " + token);
        return req;
    }

    @Test
    void setsActingMemberContext_forValidActiveToken() throws Exception {
        var principal = personal("u-1", "default", 7L);
        when(credentials.authenticate("axp_ok")).thenReturn(Optional.of(principal));
        when(members.isActive("default", "u-1")).thenReturn(true);

        sut.doFilter(bearer("axp_ok"), response, chain);

        var auth = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getToken().getSubject()).isEqualTo("u-1");
        assertThat(auth.getToken().getClaimAsString("org")).isEqualTo("default");
        assertThat(auth.getToken().getClaimAsStringList("scopes")).containsExactly("read");
        Object tokenIdClaim = auth.getToken().getClaim("tokenId");
        assertThat(tokenIdClaim).isEqualTo(7L);
        assertThat(auth.getToken().getClaims()).doesNotContainKey("connectionId");
        verify(members).isActive("default", "u-1");
        verify(credentials).recordUsage(principal);
        verify(chain).doFilter(any(), any());
    }

    /**
     * The MCP streamable transport answers with an async {@code ServerResponse}, so Tomcat performs an ASYNC dispatch
     * on which this filter skips itself ({@code shouldNotFilterAsyncDispatch()} is {@code true} by default). Under
     * {@code STATELESS}, Spring Security installs a {@link RequestAttributeSecurityContextRepository}; unless the
     * context is explicitly saved there, {@code SecurityContextHolderFilter} reloads an empty context on that dispatch
     * and {@code AuthorizationFilter} denies the already-authenticated request.
     */
    @Test
    void savesContextToRequestAttribute_soItSurvivesTheAsyncDispatch() throws Exception {
        when(credentials.authenticate("axp_ok")).thenReturn(Optional.of(personal("u-1", "default", 7L)));
        when(members.isActive("default", "u-1")).thenReturn(true);
        var req = bearer("axp_ok");

        sut.doFilter(req, response, chain);

        // Simulate the ASYNC dispatch: the holder is cleared, only the request survives.
        SecurityContextHolder.clearContext();
        var reloaded = new RequestAttributeSecurityContextRepository().loadDeferredContext(req).get();

        assertThat(reloaded.getAuthentication()).isNotNull();
        assertThat(((JwtAuthenticationToken) reloaded.getAuthentication()).getToken().getSubject()).isEqualTo("u-1");
    }

    @Test
    void rejects_whenNoBearer() throws Exception {
        sut.doFilter(new MockHttpServletRequest("POST", "/mcp"), response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
        assertThat(response.getContentAsString()).contains("\"status\":401");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void missingBearer_challengesWithTheResourceMetadata() throws Exception {

        // when
        sut.doFilter(new MockHttpServletRequest("POST", "/mcp"), response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer " + RESOURCE_METADATA);
    }

    @Test
    void aNonBearerScheme_challengesLikeAMissingBearer() throws Exception {

        // given
        var request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        // when
        sut.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer " + RESOURCE_METADATA);
        verify(credentials, never()).authenticate(any());
    }

    @Test
    void unknownBearer_challengesWithInvalidToken() throws Exception {

        // given
        var request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("Authorization", "Bearer axa_unknown");
        when(credentials.authenticate("axa_unknown")).thenReturn(Optional.empty());

        // when
        sut.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate"))
                .isEqualTo("Bearer error=\"invalid_token\", " + RESOURCE_METADATA);
        assertThat(response.getContentAsString()).contains("\"status\":401");
    }

    @Test
    void aConnectionWhoseClientIsNoLongerTrusted_isChallengedWithInvalidToken() throws Exception {

        // given: a live connection of an active member, whose client's host the allowlist no longer names
        var untrusting = new AgentCredentials(personalTokens, connections, new McpOAuthProperties(Set.of()));
        sut = filter(untrusting, rateLimiter("mcp-calls", 1000), rateLimiter("mcp-auth-failure", 1000));
        when(connections.authenticate("axa_live")).thenReturn(Optional.of(new ConnectionPrincipal("u-1", "acme",
                Set.of("read"), 4L, "https://claude.ai/oauth/claude-code-client-metadata")));
        lenient().when(members.isActive("acme", "u-1")).thenReturn(true);

        // when
        sut.doFilter(bearer("axa_live"), response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate"))
                .isEqualTo("Bearer error=\"invalid_token\", " + RESOURCE_METADATA);
        verify(chain, never()).doFilter(any(), any());
        verify(connections, never()).recordUsage(anyString(), any());
    }

    @Test
    void connectionToken_authenticatesWithAConnectionIdClaim() throws Exception {

        // given
        var request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("Authorization", "Bearer axa_ok");
        var principal = new AgentPrincipal("u-1", "acme", Set.of("read"), Credential.CONNECTION, 4L);
        when(credentials.authenticate("axa_ok")).thenReturn(Optional.of(principal));
        when(members.isActive("acme", "u-1")).thenReturn(true);
        when(access.isAvailableTo("acme", "u-1")).thenReturn(true);
        var seen = new AtomicReference<Jwt>();
        FilterChain capturing = (req, res) -> seen
                .set(((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken());

        // when
        sut.doFilter(request, response, capturing);

        // then
        assertThat(seen.get().getClaims()).containsEntry("connectionId", 4L).doesNotContainKey("tokenId");
        verify(credentials).recordUsage(principal);
    }

    @Test
    void connectionToken_isRateLimitedApartFromAPersonalTokenWithTheSameId() throws Exception {

        // given - the personal token 7's budget is spent, and connection 7 is a different credential
        sut = filter(spentCallLimiter(), rateLimiter("mcp-auth-failure", 1000));
        var principal = new AgentPrincipal("u-1", "acme", Set.of("read"), Credential.CONNECTION, 7L);
        when(credentials.authenticate("axa_ok")).thenReturn(Optional.of(principal));
        when(members.isActive("acme", "u-1")).thenReturn(true);

        // when
        sut.doFilter(bearer("axa_ok"), response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void rejects_whenBearerBlank_withoutCallingAuthenticate() throws Exception {

        sut.doFilter(bearer("   "), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(credentials, never()).authenticate(any());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejects_whenTokenInvalid() throws Exception {
        when(credentials.authenticate(anyString())).thenReturn(Optional.empty());
        sut.doFilter(bearer("axp_bad"), response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejects_whenOwnerInactive_andDoesNotRecordUsage() throws Exception {
        when(credentials.authenticate("axp_dead")).thenReturn(Optional.of(personal("u-x", "default", 9L)));
        when(members.isActive("default", "u-x")).thenReturn(false);

        sut.doFilter(bearer("axp_dead"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate"))
                .isEqualTo("Bearer error=\"invalid_token\", " + RESOURCE_METADATA);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(credentials, never()).recordUsage(any());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void synthesizedJwtCarriesNoGroups_soOnlyUserBoundPermissionsApply() throws Exception {

        // given
        when(credentials.authenticate("axp_ok")).thenReturn(Optional.of(personal("u-1", "default", 7L)));
        when(members.isActive("default", "u-1")).thenReturn(true);

        // when
        sut.doFilter(bearer("axp_ok"), response, chain);

        // then - agents act with USER-bound permissions only
        var auth = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getToken().getClaimAsStringList("groups")).isEmpty();
    }

    @Test
    void returns429_whenTokenCallLimitExceeded() throws Exception {

        // given - a call limiter whose budget for this token id is already spent, and nothing refills it
        sut = filter(spentCallLimiter(), rateLimiter("mcp-auth-failure", 1000));
        when(credentials.authenticate("axp_ok")).thenReturn(Optional.of(personal("u-1", "default", 7L)));

        // when
        sut.doFilter(bearer("axp_ok"), response, chain);

        // then - a capacity-1 bucket refilling once per minute reports the full 60s wait when the clock hasn't moved
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
        verify(chain, never()).doFilter(any(), any());
        verify(credentials, never()).recordUsage(any());
    }

    @Test
    void returns429_whenAuthFailuresExceedTheIpLimit() throws Exception {

        // given - a failure limiter admitting one failure per window, and nothing refills it
        sut = filter(rateLimiter("mcp-calls", 1000), rateLimiter("mcp-auth-failure", 1));
        when(credentials.authenticate(anyString())).thenReturn(Optional.empty());
        var first = new MockHttpServletResponse();
        sut.doFilter(bearer("axp_bad"), first, chain);
        assertThat(first.getStatus()).isEqualTo(401);

        // when - the second failed attempt from the same address
        var second = new MockHttpServletResponse();
        sut.doFilter(bearer("axp_bad"), second, chain);

        // then - a capacity-1 bucket refilling once per minute reports the full 60s wait when the clock hasn't moved
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void returns429_whenAuthFailuresExceedTheIpLimit_onMissingBearer() throws Exception {

        // given - a failure limiter admitting one failure per window
        sut = filter(rateLimiter("mcp-calls", 1000), rateLimiter("mcp-auth-failure", 1));
        var first = new MockHttpServletResponse();
        sut.doFilter(new MockHttpServletRequest("POST", "/mcp"), first, chain);
        assertThat(first.getStatus()).isEqualTo(401);

        // when - the second bearer-less request from the same address
        var second = new MockHttpServletResponse();
        sut.doFilter(new MockHttpServletRequest("POST", "/mcp"), second, chain);

        // then
        assertThat(second.getStatus()).isEqualTo(429);
    }

    @Test
    void authFailureBudgetIsPerAddressNotShared() throws Exception {

        // given - a failure limiter admitting one failure per window, shared across two callers
        sut = filter(rateLimiter("mcp-calls", 1000), rateLimiter("mcp-auth-failure", 1));
        when(credentials.authenticate(anyString())).thenReturn(Optional.empty());
        var first = new MockHttpServletResponse();
        sut.doFilter(stampedBearer("axp_bad", "198.51.100.1"), first, chain);
        var second = new MockHttpServletResponse();
        sut.doFilter(stampedBearer("axp_bad", "198.51.100.1"), second, chain);

        // when - a different address makes its own first attempt
        var other = new MockHttpServletResponse();
        sut.doFilter(stampedBearer("axp_bad", "198.51.100.2"), other, chain);

        // then - the second attempt from the first address is refused, but the other address is unaffected
        assertThat(first.getStatus()).isEqualTo(401);
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(other.getStatus()).isEqualTo(401);
    }

    private MockHttpServletRequest stampedBearer(String token, String address) {
        var req = bearer(token);
        req.addHeader(ClientAddress.HEADER, address);
        return req;
    }

    @Test
    void returns429_whenAuthFailuresExceedTheIpLimit_onInactiveOwner() throws Exception {

        // given - a failure limiter admitting one failure per window
        sut = filter(rateLimiter("mcp-calls", 1000), rateLimiter("mcp-auth-failure", 1));
        when(credentials.authenticate("axp_dead")).thenReturn(Optional.of(personal("u-x", "default", 9L)));
        when(members.isActive("default", "u-x")).thenReturn(false);
        var first = new MockHttpServletResponse();
        sut.doFilter(bearer("axp_dead"), first, chain);
        assertThat(first.getStatus()).isEqualTo(401);

        // when - the second inactive-owner attempt from the same address
        var second = new MockHttpServletResponse();
        sut.doFilter(bearer("axp_dead"), second, chain);

        // then
        assertThat(second.getStatus()).isEqualTo(429);
    }

    @Test
    void refusesATokenWhoseOrganizationMayNotConnectAgents() throws Exception {

        // given: a token that is valid in every other respect
        when(credentials.authenticate("axp_good")).thenReturn(Optional.of(personal("alice", "acme", 7L)));
        when(members.isActive("acme", "alice")).thenReturn(true);
        when(access.isAvailableTo("acme", "alice")).thenReturn(false);

        // when
        sut.doFilter(bearer("axp_good"), response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate"))
                .isEqualTo("Bearer error=\"invalid_token\", " + RESOURCE_METADATA);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, never()).doFilter(any(), any());
        verify(credentials, never()).recordUsage(any());
    }

    @Test
    void aLapsedStandingStopsTheTokenWithoutRevokingIt() throws Exception {

        // given: a hub member's token that used to carry the capability through a standing that has since lapsed
        when(credentials.authenticate("axp_good")).thenReturn(Optional.of(personal("alice", "guild", 7L)));
        when(members.isActive("guild", "alice")).thenReturn(true);
        when(access.isAvailableTo("guild", "alice")).thenReturn(false);

        // when
        sut.doFilter(bearer("axp_good"), response, chain);

        // then: refused, but nothing beyond the lookup touched the credential - it stays listed and revocable
        assertThat(response.getStatus()).isEqualTo(401);
        verify(credentials).authenticate("axp_good");
        verifyNoMoreInteractions(credentials);
    }

    // RFC 6750 and RFC 7235 details of the bearer challenge

    @Test
    void aBlankBearer_isAnInvalidRequest() throws Exception {

        // when
        sut.doFilter(bearer("   "), response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate"))
                .isEqualTo("Bearer error=\"invalid_request\", " + RESOURCE_METADATA);
        verify(credentials, never()).authenticate(any());
    }

    @Test
    void theBearerSchemeIsCaseInsensitive() throws Exception {

        // given
        var request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("Authorization", "bEaReR axp_ok");
        when(credentials.authenticate("axp_ok")).thenReturn(Optional.of(personal("u-1", "default", 7L)));
        when(members.isActive("default", "u-1")).thenReturn(true);

        // when
        sut.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }
}
