package dev.axonia.agent.application;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClientMetadataTest {

    private static final ClientMetadata CLAUDE_CODE = new ClientMetadata(
            "https://claude.ai/oauth/claude-code-client-metadata", "Claude Code", "claude.ai",
            List.of("http://localhost/callback", "http://127.0.0.1/callback"));
    private static final ClientMetadata CLAUDE = new ClientMetadata("https://claude.ai/oauth/mcp-oauth-client-metadata",
            "Claude", "claude.ai", List.of("https://claude.ai/api/mcp/auth_callback"));

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:54321/callback", "http://localhost/callback",
            "http://127.0.0.1:3118/callback", "http://LOCALHOST:1/callback"})
    void accepts_aRegisteredLoopbackOnAnyPort(String redirectUri) {
        assertThat(CLAUDE_CODE.accepts(redirectUri)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://[::1]:3118/callback", "https://localhost:3118/callback",
            "http://localhost:3118/callback/extra", "http://localhost:3118/Callback",
            "http://localhost:3118/callback?x=1", "http://evil.example/callback",
            "http://localhost.evil.example/callback", "http://user@localhost:3118/callback",
            "http://localhost:3118/callback#frag", "not a uri", ""})
    void accepts_refusesAnythingElse(String redirectUri) {
        assertThat(CLAUDE_CODE.accepts(redirectUri)).isFalse();
    }

    @Test
    void accepts_refusesNull() {
        assertThat(CLAUDE_CODE.accepts(null)).isFalse();
    }

    @Test
    void accepts_matchesTheHostLiteralRegistered() {

        var localhostOnly = new ClientMetadata("https://claude.ai/a", "A", "claude.ai",
                List.of("http://localhost/callback"));

        assertThat(localhostOnly.accepts("http://127.0.0.1:3118/callback")).isFalse();
    }

    @Test
    void accepts_aRegisteredHostedRedirectExactly() {
        assertThat(CLAUDE.accepts("https://claude.ai/api/mcp/auth_callback")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://claude.ai/api/mcp/auth_callback/extra", "https://claude.ai/api/mcp/Auth_callback",
            "https://claude.ai/api/mcp/auth_callback?x=1", "https://claude.ai:443/api/mcp/auth_callback",
            "https://CLAUDE.ai/api/mcp/auth_callback", "http://claude.ai/api/mcp/auth_callback",
            "https://evil.example/api/mcp/auth_callback", "https://claude.ai/other",
            "http://localhost/api/mcp/auth_callback"})
    void accepts_refusesAnyOtherHostedRedirect(String redirectUri) {
        assertThat(CLAUDE.accepts(redirectUri)).isFalse();
    }

    @Test
    void accepts_refusesAHostedRedirectOffTheClientHostEvenWhenRegistered() {

        var offHost = new ClientMetadata("https://claude.ai/a", "A", "claude.ai",
                List.of("https://evil.example/callback"));

        assertThat(offHost.accepts("https://evil.example/callback")).isFalse();
    }

    @Test
    void accepts_matchesALoopbackOnlyAgainstARegisteredLoopback() {

        var hostedLocalhost = new ClientMetadata("https://localhost/a", "A", "localhost",
                List.of("https://localhost/callback"));

        assertThat(hostedLocalhost.accepts("http://localhost:3118/callback")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1/cb", "http://localhost/cb", "https://claude.ai/cb",
            "https://CLAUDE.AI/cb"})
    void isPermittedRedirect_loopbackOrTheClientHost(String uri) {
        assertThat(ClientMetadata.isPermittedRedirect(URI.create(uri), "claude.ai")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/cb", "https://localhost/cb", "http://u@localhost/cb",
            "http://claude.ai/cb", "https://api.claude.ai/cb", "https://claude.ai:8443/cb", "https://u@claude.ai/cb",
            "https://claude.ai/cb#f", "urn:x", "https:///cb"})
    void isPermittedRedirect_refusesAnythingElse(String uri) {
        assertThat(ClientMetadata.isPermittedRedirect(URI.create(uri), "claude.ai")).isFalse();
    }
}
