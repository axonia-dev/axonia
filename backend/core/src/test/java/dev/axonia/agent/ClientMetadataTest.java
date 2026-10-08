package dev.axonia.agent;

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
    void isLoopback() {
        assertThat(ClientMetadata.isLoopback(URI.create("http://127.0.0.1/cb"))).isTrue();
        assertThat(ClientMetadata.isLoopback(URI.create("http://localhost/cb"))).isTrue();
        assertThat(ClientMetadata.isLoopback(URI.create("http://example.com/cb"))).isFalse();
        assertThat(ClientMetadata.isLoopback(URI.create("https://localhost/cb"))).isFalse();
        assertThat(ClientMetadata.isLoopback(URI.create("http://u@localhost/cb"))).isFalse();
    }
}
