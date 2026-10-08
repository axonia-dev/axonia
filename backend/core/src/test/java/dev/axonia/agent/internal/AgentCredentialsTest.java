package dev.axonia.agent.internal;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.agent.internal.AgentPrincipal.Credential;
import dev.axonia.identity.connection.AgentConnections;
import dev.axonia.identity.connection.ConnectionPrincipal;
import dev.axonia.identity.pat.PersonalAccessTokens;
import dev.axonia.identity.pat.PersonalTokenPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class AgentCredentialsTest {

    private static final String CLAUDE_CODE = "https://claude.ai/oauth/claude-code-client-metadata";

    @Mock
    PersonalAccessTokens personalTokens;

    @Mock
    AgentConnections connections;

    AgentCredentials sut;

    @BeforeEach
    void setUp() {
        sut = new AgentCredentials(personalTokens, connections, new McpOAuthProperties(Set.of("claude.ai")));
    }

    @Test
    void authenticate_aConnectionToken() {

        when(connections.authenticate("axa_1"))
                .thenReturn(Optional.of(new ConnectionPrincipal("u-1", "acme", Set.of("read"), 4L, CLAUDE_CODE)));

        var actual = sut.authenticate("axa_1");

        assertThat(actual).contains(new AgentPrincipal("u-1", "acme", Set.of("read"), Credential.CONNECTION, 4L));
        verify(personalTokens, never()).authenticate(anyString());
    }

    @Test
    void authenticate_refusesAConnectionWhoseClientIsNoLongerTrusted() {

        // given
        when(connections.authenticate("axa_1")).thenReturn(Optional
                .of(new ConnectionPrincipal("u-1", "acme", Set.of("read"), 4L, "https://dropped.example/client")));

        // when
        var actual = sut.authenticate("axa_1");

        // then
        assertThat(actual).isEmpty();
        verify(connections, never()).recordUsage(anyString(), any());
    }

    @Test
    void authenticate_aPersonalToken() {

        when(personalTokens.authenticate("axp_1"))
                .thenReturn(Optional.of(new PersonalTokenPrincipal("u-1", "acme", Set.of("read"), 7L)));

        var actual = sut.authenticate("axp_1");

        assertThat(actual).contains(new AgentPrincipal("u-1", "acme", Set.of("read"), Credential.PERSONAL_TOKEN, 7L));
        verify(connections, never()).authenticate(anyString());
    }

    @Test
    void recordUsage_touchesTheRightCredential() {

        sut.recordUsage(new AgentPrincipal("u-1", "acme", Set.of(), Credential.CONNECTION, 4L));
        sut.recordUsage(new AgentPrincipal("u-1", "acme", Set.of(), Credential.PERSONAL_TOKEN, 7L));

        verify(connections).recordUsage("acme", 4L);
        verify(personalTokens).recordUsage("acme", 7L);
    }

    @Test
    void principal_keysEachCredentialApart() {

        assertThat(new AgentPrincipal("u", "o", Set.of(), Credential.CONNECTION, 7L).rateLimitKey())
                .isEqualTo("connection:7");
        assertThat(new AgentPrincipal("u", "o", Set.of(), Credential.PERSONAL_TOKEN, 7L).rateLimitKey())
                .isEqualTo("token:7");
    }
}
