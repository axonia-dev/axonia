package dev.axonia.agent.adapter.in.web;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.FakeRateLimitStore;
import dev.axonia.agent.application.AuthorizationOutcome.Ready;
import dev.axonia.agent.application.AuthorizationOutcome.Redirect;
import dev.axonia.agent.application.AuthorizationOutcome.Refused;
import dev.axonia.agent.application.AuthorizationRequest;
import dev.axonia.agent.application.McpAuthorization;
import dev.axonia.agent.configuration.AgentSecurityConfig;
import dev.axonia.identity.api.connection.AgentConnections;
import dev.axonia.identity.api.connection.ConnectionSummary;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.ratelimit.Budget;
import dev.axonia.shared.api.ratelimit.RateLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@CoreWebMvcTest(controllers = AgentConnectionController.class)
@Import(AgentConnectionControllerTest.Limits.class)
class AgentConnectionControllerTest extends ControllerTestSupport {

    private static final String REQUEST = """
            {"response_type":"code","client_id":"https://claude.ai/c","redirect_uri":"http://localhost:1/callback",
             "code_challenge":"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM","code_challenge_method":"S256",
             "scope":"read","state":"s-1","unknown_extra":"ignored"}""";

    private static final int CONSENT_BUDGET = 100;

    private static final String APPROVE = "/axonia/me/agent-connections/authorization-requests/approve";

    @MockitoBean
    McpAuthorization authorization;

    @Autowired
    AgentSecurityConfig.McpRateLimiters limiters;

    @TestConfiguration(proxyBeanMethods = false)
    static class Limits {

        @Bean
        AgentSecurityConfig.McpRateLimiters limiters() {

            var store = new FakeRateLimitStore();
            var meters = new SimpleMeterRegistry();
            var budget = new Budget(CONSENT_BUDGET, Duration.ofMinutes(1));
            return new AgentSecurityConfig.McpRateLimiters(new RateLimiter("c", budget, store, meters),
                    new RateLimiter("f", budget, store, meters), new RateLimiter("t", budget, store, meters),
                    new RateLimiter("consent", budget, store, meters));
        }
    }

    @MockitoBean
    AgentConnections connections;

    @Test
    void review_answersReadyWithItsDiscriminator() {

        // given
        when(authorization.review(any())).thenReturn(new Ready("Claude Code", "claude.ai", List.of("read")));

        // when
        var actual = mvc.post().uri("/axonia/me/agent-connections/authorization-requests")
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST);

        // then
        actual.assertThat().hasStatusOk().bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.outcome").isEqualTo("READY");
            json.assertThat().extractingPath("$.clientName").isEqualTo("Claude Code");
            json.assertThat().extractingPath("$.requestedScopes[0]").isEqualTo("read");
        });
        verify(authorization).review(AuthorizationRequest.builder().responseType("code").clientId("https://claude.ai/c")
                .redirectUri("http://localhost:1/callback").codeChallenge("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                .codeChallengeMethod("S256").scope("read").state("s-1").build());
    }

    @Test
    void approve_answersTheRedirect_whenTheCardShowedTheCallersOrganization() {

        // given
        when(authorization.approve(any(), eq(Set.of("read")))).thenReturn(new Redirect("http://localhost:1/cb?code=x"));

        // when
        var actual = mvc.post().uri(APPROVE).contentType(MediaType.APPLICATION_JSON).content(approval("default"));

        // then
        actual.assertThat().hasStatusOk().bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.outcome").isEqualTo("REDIRECT");
            json.assertThat().extractingPath("$.redirectUrl").isEqualTo("http://localhost:1/cb?code=x");
        });
    }

    @Test
    void approve_answers409WithoutACode_whenTheCallersOrganizationChangedSinceTheCard() {

        // when
        var actual = mvc.post().uri(APPROVE).contentType(MediaType.APPLICATION_JSON).content(approval("other-org"));

        // then
        actual.assertThat().hasStatus(409).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.status").isEqualTo(409);
            json.assertThat().extractingPath("$.code").isEqualTo("ORGANIZATION_CHANGED");
        });
        verifyNoInteractions(authorization);
    }

    @Test
    void approve_leavesAnOrganizationlessCallerToTheAuthorizationRefusal() {

        // given
        when(authorization.approve(any(), eq(Set.of("read")))).thenReturn(new Refused("NO_AGENT_ACCESS"));

        // when
        var actual = mvc.post().uri(APPROVE).with(jwt().jwt(token -> token.subject("u-orgless")))
                .contentType(MediaType.APPLICATION_JSON).content(approval("default"));

        // then
        actual.assertThat().hasStatusOk().bodyJson().extractingPath("$.reason").isEqualTo("NO_AGENT_ACCESS");
    }

    @Test
    void approve_answers400WithoutAnOrganization() {

        var actual = mvc.post().uri(APPROVE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"request\":" + REQUEST + ",\"scopes\":[\"read\"]}");

        actual.assertThat().hasStatus(400);
        verifyNoInteractions(authorization);
    }

    @Test
    void everyConsentRoute_answers429OnceThePersonsBudgetIsSpent() {

        // given
        var subject = "u-spent";
        for (var i = 0; i < CONSENT_BUDGET; i++) {
            limiters.consentRequests().tryConsume(subject);
        }

        for (var route : List.of("/authorization-requests", "/authorization-requests/approve",
                "/authorization-requests/deny")) {
            // when
            var actual = mvc.post().uri("/axonia/me/agent-connections" + route)
                    .with(jwt().jwt(token -> token.subject(subject).claim("org", "default")))
                    .contentType(MediaType.APPLICATION_JSON).content(approval("default"));

            // then
            actual.assertThat().hasStatus(429).containsHeader("Retry-After").headers().hasValue("Cache-Control",
                    "no-store");
        }
        verifyNoInteractions(authorization);
    }

    @Test
    void deny_answersARefusal() {

        when(authorization.deny(any())).thenReturn(new Refused("UNTRUSTED_CLIENT"));

        var actual = mvc.post().uri("/axonia/me/agent-connections/authorization-requests/deny")
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST);

        actual.assertThat().hasStatusOk().bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.outcome").isEqualTo("REFUSED");
            json.assertThat().extractingPath("$.reason").isEqualTo("UNTRUSTED_CLIENT");
        });
    }

    @Test
    void approve_answers400WithoutAScopeSet() {

        var actual = mvc.post().uri(APPROVE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"request\":" + REQUEST + ",\"organizationId\":\"default\"}");

        actual.assertThat().hasStatus(400);
        verify(authorization, never()).approve(any(), any());
    }

    private static String approval(String organizationId) {
        return "{\"request\":" + REQUEST + ",\"scopes\":[\"read\"],\"organizationId\":\"" + organizationId + "\"}";
    }

    @Test
    void list_readsTheCallersOwnConnections() {

        when(connections.list(DEFAULT_TEST_USER, "default"))
                .thenReturn(List.of(new ConnectionSummary(4L, "Claude Code", "claude.ai", Set.of("read"),
                        Instant.parse("2026-09-24T10:00:00Z"), null, Instant.parse("2027-03-23T10:00:00Z"), "ACTIVE")));

        var actual = mvc.get().uri("/axonia/me/agent-connections");

        actual.assertThat().hasStatusOk().bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$[0].clientName").isEqualTo("Claude Code");
            json.assertThat().extractingPath("$[0].status").isEqualTo("ACTIVE");
            json.assertThat().extractingPath("$[0].activatedAt").isEqualTo("2026-09-24T10:00:00Z");
            json.assertThat().extractingPath("$[0].endsAt").isEqualTo("2027-03-23T10:00:00Z");
        });
    }

    @Test
    void disconnect_revokesTheCallersConnection() {

        mvc.delete().uri("/axonia/me/agent-connections/4").assertThat().hasStatus(204);

        verify(connections).revoke(DEFAULT_TEST_USER, "default", 4L);
    }

    @Test
    void disconnect_answers404ForAnotherPersonsConnection() {

        doThrow(new NotFoundException("nope")).when(connections).revoke(DEFAULT_TEST_USER, "default", 9L);

        mvc.delete().uri("/axonia/me/agent-connections/9").assertThat().hasStatus(404);
    }
}
