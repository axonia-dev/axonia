package dev.axonia.agent.application;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.util.UriComponentsBuilder;

import dev.axonia.agent.adapter.out.http.UriComponentsClientRedirects;
import dev.axonia.agent.application.AuthorizationOutcome.Ready;
import dev.axonia.agent.application.AuthorizationOutcome.Redirect;
import dev.axonia.agent.application.AuthorizationOutcome.Refused;
import dev.axonia.agent.application.port.out.ClientMetadataDocuments;
import dev.axonia.identity.api.connection.AgentConnections;
import dev.axonia.identity.api.connection.AuthorizeConnection;
import dev.axonia.identity.api.pat.MemberAccounts;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class McpAuthorizationTest {

    private static final String CLIENT = "https://claude.ai/oauth/claude-code-client-metadata";
    private static final String REDIRECT = "http://localhost:54321/callback";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final ClientMetadata CLAUDE_CODE = new ClientMetadata(CLIENT, "Claude Code", "claude.ai",
            List.of("http://localhost/callback"));

    @Mock
    ClientMetadataDocuments documents;

    @Mock
    AgentConnections connections;

    @Mock
    AgentAccess access;

    @Mock
    MemberAccounts members;

    McpAuthorization sut;

    @BeforeEach
    void setUp() {

        var endpoints = new McpEndpoints(
                new dev.axonia.shared.configuration.PublicUrlProperties("https://axonia.test"));
        sut = new McpAuthorization(documents, connections, access, members, endpoints,
                new UriComponentsClientRedirects());
        signIn("u-1", "acme");
        lenient().when(documents.resolve(CLIENT)).thenReturn(Optional.of(CLAUDE_CODE));
        lenient().when(access.isAvailableToCaller()).thenReturn(true);
        lenient().when(members.isActive("acme", "u-1")).thenReturn(true);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void review_readyWithTheRequestedKnownScopes() {

        var actual = sut.review(request().scope("read topic:propose bogus").build());

        assertThat(actual).isEqualTo(new Ready("Claude Code", "claude.ai", List.of("read", "topic:propose")));
    }

    @Test
    void review_defaultsToReadWhenNothingKnownIsRequested() {

        assertThat(sut.review(request().scope(null).build()))
                .isEqualTo(new Ready("Claude Code", "claude.ai", List.of("read")));
        assertThat(sut.review(request().scope("bogus").build()))
                .isEqualTo(new Ready("Claude Code", "claude.ai", List.of("read")));
    }

    @Test
    void review_refusesAnUntrustedClientWithoutRedirecting() {

        when(documents.resolve(CLIENT)).thenReturn(Optional.empty());

        assertThat(sut.review(request().build())).isEqualTo(new Refused("UNTRUSTED_CLIENT"));
    }

    @Test
    void review_refusesAnUnregisteredRedirectWithoutRedirecting_evenWhenOtherParametersAreWrong() {

        var actual = sut.review(request().redirectUri("http://evil.example/cb").responseType("token").build());

        assertThat(actual).isEqualTo(new Refused("REDIRECT_MISMATCH"));
    }

    @Test
    void review_refusesACallerWithoutAgentAccess() {

        when(access.isAvailableToCaller()).thenReturn(false);

        assertThat(sut.review(request().build())).isEqualTo(new Refused("NO_AGENT_ACCESS"));
    }

    @Test
    void review_refusesAnInactiveMember() {

        when(members.isActive("acme", "u-1")).thenReturn(false);

        assertThat(sut.review(request().build())).isEqualTo(new Refused("NO_AGENT_ACCESS"));
    }

    @Test
    void review_refusesAnOrganizationlessCaller() {

        signIn("u-1", null);

        assertThat(sut.review(request().build())).isEqualTo(new Refused("NO_AGENT_ACCESS"));
    }

    @Test
    void review_refusesAnIneligibleCallerWithoutFetchingTheDocument() {

        // given
        when(access.isAvailableToCaller()).thenReturn(false);

        // when
        var reviewed = sut.review(request().build());
        var approved = sut.approve(request().build(), Set.of("read"));
        var denied = sut.deny(request().build());

        // then
        assertThat(List.of(reviewed, approved, denied)).containsOnly(new Refused("NO_AGENT_ACCESS"));
        verifyNoInteractions(documents);
        verify(connections, never()).authorize(any());
    }

    @Test
    void review_redirectsAnUnsupportedResponseType() {

        var actual = (Redirect) sut.review(request().responseType("token").build());

        assertThat(query(actual, "error")).isEqualTo("unsupported_response_type");
        assertThat(query(actual, "state")).isEqualTo("s-1");
        assertThat(query(actual, "iss")).isEqualTo("https://axonia.test");
    }

    @Test
    void review_redirectsAMissingOrPlainPkce() {

        assertThat(query((Redirect) sut.review(request().codeChallenge(null).build()), "error"))
                .isEqualTo("invalid_request");
        assertThat(query((Redirect) sut.review(request().codeChallengeMethod("plain").build()), "error"))
                .isEqualTo("invalid_request");
    }

    @Test
    void review_redirectsAnotherResource_andAcceptsNone() {

        assertThat(query((Redirect) sut.review(request().resource("https://elsewhere.test/mcp").build()), "error"))
                .isEqualTo("invalid_target");
        assertThat(sut.review(request().resource(null).build())).isInstanceOf(Ready.class);
    }

    @Test
    void approve_bindsTheCallersCurrentOrganization_andRedirectsWithTheCode() {

        // given
        signIn("u-1", "acme");
        when(connections.authorize(any())).thenReturn("axc_code");

        // when
        var actual = (Redirect) sut.approve(request().build(), Set.of("read", "topic:propose", "bogus"));

        // then
        var command = ArgumentCaptor.forClass(AuthorizeConnection.class);
        verify(connections).authorize(command.capture());
        assertThat(command.getValue())
                .isEqualTo(AuthorizeConnection.builder().userId("u-1").organizationId("acme").clientId(CLIENT)
                        .clientName("Claude Code").clientHost("claude.ai").scopes(Set.of("read", "topic:propose"))
                        .resource("https://axonia.test/mcp").redirectUri(REDIRECT).codeChallenge(CHALLENGE).build());
        assertThat(actual.redirectUrl()).startsWith(REDIRECT + "?");
        assertThat(query(actual, "code")).isEqualTo("axc_code");
        assertThat(query(actual, "state")).isEqualTo("s-1");
        assertThat(query(actual, "iss")).isEqualTo("https://axonia.test");
    }

    @Test
    void approve_encodesStateStrictly() {

        // given
        when(connections.authorize(any())).thenReturn("axc_code");
        var state = "a&b=c d+é/?#%";

        // when
        var actual = (Redirect) sut.approve(request().state(state).build(), Set.of("read"));

        // then
        assertThat(actual.redirectUrl()).doesNotContain(" ").doesNotContain("é").contains("iss=");
        assertThat(query(actual, "state")).isEqualTo(state);
        assertThat(query(actual, "code")).isEqualTo("axc_code");
    }

    @Test
    void approve_omitsAnAbsentState() {

        when(connections.authorize(any())).thenReturn("axc_code");

        var actual = (Redirect) sut.approve(request().state(null).build(), Set.of("read"));

        assertThat(actual.redirectUrl()).doesNotContain("state=");
    }

    @Test
    void approve_refusesAnEmptyGrant() {

        assertThatThrownBy(() -> sut.approve(request().build(), Set.of("bogus")))
                .isInstanceOf(ValidationException.class);
        verify(connections, never()).authorize(any());
    }

    @Test
    void approve_revalidates() {

        when(documents.resolve(CLIENT)).thenReturn(Optional.empty());

        assertThat(sut.approve(request().build(), Set.of("read"))).isEqualTo(new Refused("UNTRUSTED_CLIENT"));
        verify(connections, never()).authorize(any());
    }

    @Test
    void deny_redirectsWithAccessDenied() {

        var actual = (Redirect) sut.deny(request().build());

        assertThat(query(actual, "error")).isEqualTo("access_denied");
        assertThat(query(actual, "state")).isEqualTo("s-1");
        verify(connections, never()).authorize(any());
    }

    private static AuthorizationRequest.AuthorizationRequestBuilder request() {

        return AuthorizationRequest.builder().responseType("code").clientId(CLIENT).redirectUri(REDIRECT)
                .codeChallenge(CHALLENGE).codeChallengeMethod("S256").resource("https://axonia.test/mcp").scope("read")
                .state("s-1");
    }

    /** Decodes a query parameter back, which is what the client does. */
    private static String query(Redirect redirect, String name) {

        var value = UriComponentsBuilder.fromUri(URI.create(redirect.redirectUrl())).build().getQueryParams()
                .getFirst(name);
        return value == null ? null : java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void signIn(String subject, String organization) {

        var builder = Jwt.withTokenValue("t").header("alg", "none").subject(subject);
        if (organization != null) {
            builder.claim("org", organization);
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(builder.build(), List.of()));
        SecurityContextHolder.setContext(context);
    }
}
