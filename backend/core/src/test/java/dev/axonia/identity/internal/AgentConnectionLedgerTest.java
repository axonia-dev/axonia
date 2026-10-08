package dev.axonia.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.NotFoundException;
import dev.axonia.identity.connection.ConnectionPrincipal;
import dev.axonia.identity.connection.ConnectionSummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class AgentConnectionLedgerTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");
    private static final AgentConnectionLifetimes LIFETIMES = new AgentConnectionLifetimes(Duration.ofSeconds(60),
            Duration.ofHours(1), Duration.ofDays(30), Duration.ofDays(180), Duration.ofSeconds(30),
            Duration.ofDays(30));

    @Mock
    AgentConnectionRepository connections;

    @Mock
    AgentConnectionTokenRepository tokens;

    AgentConnectionLedger sut;

    @BeforeEach
    void setUp() {
        sut = new AgentConnectionLedger(connections, tokens, LIFETIMES, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void principal_forAUsableConnection() {

        // given
        when(connections.findById(9L)).thenReturn(Optional.of(active()));

        // when
        var actual = sut.principal(access());

        // then
        assertThat(actual).contains(new ConnectionPrincipal("u-1", "default", Set.of("read"), 9L,
                "https://claude.ai/oauth/claude-code-client-metadata"));
    }

    @Test
    void principal_emptyForARevokedOrEndedConnection() {

        when(connections.findById(9L)).thenReturn(Optional.of(active().toBuilder().revokedAt(NOW).build()),
                Optional.of(active().toBuilder().endsAt(NOW).build()));

        assertThat(sut.principal(access())).isEmpty();
        assertThat(sut.principal(access())).isEmpty();
    }

    @Test
    void list_reportsEachStatus() {

        // given
        when(connections.findByOrganizationIdAndUserIdAndActivatedAtIsNotNullOrderByActivatedAtDesc("default", "u-1"))
                .thenReturn(List.of(active(), active().toBuilder().id(10L).revokedAt(NOW).build(),
                        active().toBuilder().id(11L).endsAt(NOW).build()));

        // when
        var actual = sut.list("u-1", "default");

        // then
        assertThat(actual).extracting(ConnectionSummary::status).containsExactly("ACTIVE", "REVOKED", "ENDED");
    }

    @Test
    void revoke_throwsWhenNotTheCallers() {

        when(connections.findByOrganizationIdAndUserIdAndId("default", "u-2", 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.revoke("u-2", "default", 9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void revoke_isIdempotent() {

        when(connections.findByOrganizationIdAndUserIdAndId("default", "u-1", 9L))
                .thenReturn(Optional.of(active().toBuilder().revokedAt(NOW.minusSeconds(5)).build()));

        sut.revoke("u-1", "default", 9L);

        verify(connections, never()).save(any());
    }

    @Test
    void revokeConnection_revokesOnce() {

        // given
        when(connections.findById(9L)).thenReturn(Optional.of(active()));

        // when
        sut.revokeConnection(9L);

        // then
        var saved = ArgumentCaptor.forClass(AgentConnectionEntity.class);
        verify(connections).save(saved.capture());
        assertThat(saved.getValue().getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void revokeConnection_doesNothingForAnAlreadyRevokedConnection() {

        // given
        when(connections.findById(9L))
                .thenReturn(Optional.of(active().toBuilder().revokedAt(NOW.minusSeconds(5)).build()));

        // when
        sut.revokeConnection(9L);

        // then
        verify(connections, never()).save(any());
    }

    @Test
    void recordUsage_swallowsAFailedWrite() {

        doThrow(new IllegalStateException("db down")).when(connections).touchLastUsed("default", 9L, NOW,
                NOW.minusSeconds(60));

        assertThatCode(() -> sut.recordUsage("default", 9L)).doesNotThrowAnyException();
    }

    @Test
    void sweep_deletesExpiredTokensAndRetiredConnections() {

        when(tokens.deleteExpired("default", NOW)).thenReturn(3);
        when(connections.deleteRetired("default", NOW.minus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(30))))
                .thenReturn(2);

        assertThat(sut.sweep("default")).isEqualTo(5);
    }

    // Usage is recorded at most once a minute per connection

    @Test
    void recordUsage_writesOnlyWhenTheLastWriteIsOverAMinuteOld() {

        sut.recordUsage("default", 9L);

        verify(connections).touchLastUsed("default", 9L, NOW, NOW.minus(Duration.ofMinutes(1)));
    }

    private static AgentConnectionEntity active() {

        return AgentConnectionEntity.builder().id(9L).organizationId("default").userId("u-1")
                .clientId("https://claude.ai/oauth/claude-code-client-metadata").clientName("Claude Code")
                .clientHost("claude.ai").scopes("read").resource("https://axonia.test/mcp")
                .redirectUri("http://localhost:3118/callback")
                .codeChallenge("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM").activatedAt(NOW.minusSeconds(60))
                .endsAt(NOW.plus(Duration.ofDays(180))).build();
    }

    private static AgentConnectionTokenEntity access() {

        return AgentConnectionTokenEntity.builder().id(30L).organizationId("default").connectionId(9L)
                .kind(AgentConnectionTokenKind.ACCESS).tokenHash("h").expiresAt(NOW.plusSeconds(3600)).build();
    }
}
