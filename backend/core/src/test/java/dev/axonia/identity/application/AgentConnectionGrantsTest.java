package dev.axonia.identity.application;

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

import dev.axonia.identity.api.connection.AuthorizeConnection;
import dev.axonia.identity.api.connection.RedeemCode;
import dev.axonia.identity.api.connection.RefreshConnection;
import dev.axonia.identity.application.port.out.AgentConnectionStore;
import dev.axonia.identity.application.port.out.AgentConnectionTokenStore;
import dev.axonia.identity.configuration.AgentConnectionLifetimes;
import dev.axonia.identity.domain.AgentConnection;
import dev.axonia.identity.domain.AgentConnectionToken;
import dev.axonia.identity.domain.AgentConnectionTokenKind;
import dev.axonia.shared.api.secret.TokenHasher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class AgentConnectionGrantsTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");
    private static final String CLIENT = "https://claude.ai/oauth/claude-code-client-metadata";
    private static final String REDIRECT = "http://localhost:3118/callback";
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final AgentConnectionLifetimes LIFETIMES = new AgentConnectionLifetimes(Duration.ofSeconds(60),
            Duration.ofHours(1), Duration.ofDays(30), Duration.ofDays(180), Duration.ofSeconds(30),
            Duration.ofDays(30));

    @Mock
    AgentConnectionStore connections;

    @Mock
    AgentConnectionTokenStore tokens;

    AgentConnectionGrants sut;

    @BeforeEach
    void setUp() {

        sut = new AgentConnectionGrants(connections, tokens, LIFETIMES, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(tokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void authorize_savesAPendingConnectionAndAOneMinuteCode() {

        // given
        when(connections.save(any()))
                .thenAnswer(invocation -> ((AgentConnection) invocation.getArgument(0)).toBuilder().id(7L).build());

        // when
        var actual = sut.authorize(AuthorizeConnection.builder().userId("u-1").organizationId("default")
                .clientId(CLIENT).clientName("Claude Code").clientHost("claude.ai").scopes(Set.of("read"))
                .resource("https://axonia.test/mcp").redirectUri(REDIRECT).codeChallenge(CHALLENGE).build());

        // then
        assertThat(actual).startsWith("axc_");
        var connection = ArgumentCaptor.forClass(AgentConnection.class);
        verify(connections).save(connection.capture());
        assertThat(connection.getValue().activatedAt()).isNull();
        assertThat(connection.getValue().endsAt()).isEqualTo(NOW.plus(Duration.ofDays(180)));
        var code = ArgumentCaptor.forClass(AgentConnectionToken.class);
        verify(tokens).save(code.capture());
        assertThat(code.getValue().kind()).isEqualTo(AgentConnectionTokenKind.CODE);
        assertThat(code.getValue().connectionId()).isEqualTo(7L);
        assertThat(code.getValue().tokenHash()).isEqualTo(TokenHasher.sha256Hex(actual));
        assertThat(code.getValue().expiresAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void redeem_activatesTheConnection_revokesThePreviousOne_andIssuesAPair() {

        // given
        var code = code(NOW.plusSeconds(30));
        var previous = activeConnection(3L);
        when(tokens.consume(code.id(), NOW)).thenReturn(1);
        when(connections.findById(9L)).thenReturn(Optional.of(pendingConnection()));
        when(connections.findByOrganizationIdAndUserIdAndClientIdAndActivatedAtIsNotNullAndRevokedAtIsNull("default",
                "u-1", CLIENT)).thenReturn(List.of(previous));
        when(connections.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var actual = sut.redeem(code, new RedeemCode("axc_x", CLIENT, REDIRECT, VERIFIER));

        // then
        assertThat(actual).isPresent();
        assertThat(actual.get().accessToken()).startsWith("axa_");
        assertThat(actual.get().refreshToken()).startsWith("axr_");
        assertThat(actual.get().expiresIn()).isEqualTo(Duration.ofHours(1));
        assertThat(actual.get().scopes()).containsExactly("read");
        var saved = ArgumentCaptor.forClass(AgentConnection.class);
        verify(connections, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).anySatisfy(c -> {
            assertThat(c.id()).isEqualTo(3L);
            assertThat(c.revokedAt()).isEqualTo(NOW);
        }).anySatisfy(c -> {
            assertThat(c.id()).isEqualTo(9L);
            assertThat(c.activatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void redeem_refusesAWrongVerifier() {

        // given
        var code = code(NOW.plusSeconds(30));
        when(tokens.consume(code.id(), NOW)).thenReturn(1);
        when(connections.findById(9L)).thenReturn(Optional.of(pendingConnection()));

        // when
        var actual = sut.redeem(code, new RedeemCode("axc_x", CLIENT, REDIRECT, "y".repeat(43)));

        // then
        assertThat(actual).isEmpty();
        verify(connections, never()).save(any());
    }

    @Test
    void redeem_refusesAnotherRedirectOrClient() {

        // given
        var code = code(NOW.plusSeconds(30));
        when(tokens.consume(code.id(), NOW)).thenReturn(1);
        when(connections.findById(9L)).thenReturn(Optional.of(pendingConnection()));

        // when / then
        assertThat(sut.redeem(code, new RedeemCode("axc_x", CLIENT, "http://localhost:9/other", VERIFIER))).isEmpty();
        assertThat(sut.redeem(code, new RedeemCode("axc_x", "https://claude.ai/other", REDIRECT, VERIFIER))).isEmpty();
    }

    @Test
    void redeem_refusesAnExpiredCode() {

        // given
        var code = code(NOW);
        when(tokens.consume(code.id(), NOW)).thenReturn(1);
        when(connections.findById(9L)).thenReturn(Optional.of(pendingConnection()));

        // when / then
        assertThat(sut.redeem(code, new RedeemCode("axc_x", CLIENT, REDIRECT, VERIFIER))).isEmpty();
    }

    @Test
    void redeem_aSecondTime_revokesTheConnectionTheFirstCreated() {

        // given
        var code = code(NOW.plusSeconds(30));
        when(tokens.consume(code.id(), NOW)).thenReturn(0);
        when(connections.findById(9L)).thenReturn(Optional.of(activeConnection(9L)));

        // when
        var actual = sut.redeem(code, new RedeemCode("axc_x", CLIENT, REDIRECT, VERIFIER));

        // then
        assertThat(actual).isEmpty();
        var saved = ArgumentCaptor.forClass(AgentConnection.class);
        verify(connections).save(saved.capture());
        assertThat(saved.getValue().revokedAt()).isEqualTo(NOW);
    }

    @Test
    void refresh_rotates_andDropsTheLiveAccessToken() {

        // given
        var refresh = refreshToken(null, NOW.plus(Duration.ofDays(10)));
        when(connections.findById(9L)).thenReturn(Optional.of(activeConnection(9L)));
        when(tokens.consume(refresh.id(), NOW)).thenReturn(1);

        // when
        var actual = sut.refresh(refresh, new RefreshConnection("axr_x", CLIENT));

        // then
        assertThat(actual).isPresent();
        verify(tokens).deleteAccessTokens(9L);
    }

    @Test
    void refresh_fromAnotherClient_isRefusedWithoutConsumingOrRevoking() {

        // given
        var refresh = refreshToken(null, NOW.plus(Duration.ofDays(10)));
        when(connections.findById(9L)).thenReturn(Optional.of(activeConnection(9L)));

        // when
        var actual = sut.refresh(refresh, new RefreshConnection("axr_x", "https://claude.ai/other"));

        // then
        assertThat(actual).isEmpty();
        verify(tokens, never()).consume(any(), any());
        verify(connections, never()).save(any());
    }

    @Test
    void refresh_reuseOutsideGrace_revokesTheConnection() {

        // given
        var refresh = refreshToken(NOW.minusSeconds(31), NOW.plus(Duration.ofDays(10)));
        when(connections.findById(9L)).thenReturn(Optional.of(activeConnection(9L)));
        when(tokens.consume(refresh.id(), NOW)).thenReturn(0);

        // when
        var actual = sut.refresh(refresh, new RefreshConnection("axr_x", CLIENT));

        // then
        assertThat(actual).isEmpty();
        var saved = ArgumentCaptor.forClass(AgentConnection.class);
        verify(connections).save(saved.capture());
        assertThat(saved.getValue().revokedAt()).isEqualTo(NOW);
    }

    @Test
    void refresh_reuseInsideGrace_doesNotRevoke() {

        // given
        var refresh = refreshToken(NOW.minusSeconds(5), NOW.plus(Duration.ofDays(10)));
        when(connections.findById(9L)).thenReturn(Optional.of(activeConnection(9L)));
        when(tokens.consume(refresh.id(), NOW)).thenReturn(0);

        // when
        var actual = sut.refresh(refresh, new RefreshConnection("axr_x", CLIENT));

        // then
        assertThat(actual).isEmpty();
        verify(connections, never()).save(any());
    }

    @Test
    void refresh_refusesATokenPastItsIdleLifetime() {

        // given
        var refresh = refreshToken(null, NOW);
        when(connections.findById(9L)).thenReturn(Optional.of(activeConnection(9L)));
        when(tokens.consume(refresh.id(), NOW)).thenReturn(1);

        // when
        var actual = sut.refresh(refresh, new RefreshConnection("axr_x", CLIENT));

        // then
        assertThat(actual).isEmpty();
        verify(tokens, never()).deleteAccessTokens(any());
    }

    @Test
    void refresh_refusesAnEndedConnection_andCapsTokensAtItsEnd() {

        // given
        var ended = activeConnection(9L).toBuilder().endsAt(NOW).build();
        var endingSoon = activeConnection(9L).toBuilder().endsAt(NOW.plusSeconds(600)).build();
        var refresh = refreshToken(null, NOW.plus(Duration.ofDays(10)));
        when(tokens.consume(refresh.id(), NOW)).thenReturn(1);
        when(connections.findById(9L)).thenReturn(Optional.of(ended), Optional.of(endingSoon));

        // when
        var refused = sut.refresh(refresh, new RefreshConnection("axr_x", CLIENT));
        var capped = sut.refresh(refresh, new RefreshConnection("axr_x", CLIENT));

        // then
        assertThat(refused).isEmpty();
        assertThat(capped).isPresent().get().extracting(t -> t.expiresIn()).isEqualTo(Duration.ofSeconds(600));
    }

    // A connection swept between the token lookup and the grant answers invalid_grant

    @Test
    void redeem_answersEmptyWhenTheConnectionIsGone() {

        var code = code(NOW.plusSeconds(30));
        when(connections.findById(9L)).thenReturn(Optional.empty());

        assertThat(sut.redeem(code, new RedeemCode("axc_x", CLIENT, REDIRECT, VERIFIER))).isEmpty();
        verify(connections, never()).save(any());
    }

    @Test
    void refresh_answersEmptyWhenTheConnectionIsGone() {

        var refresh = refreshToken(null, NOW.plus(Duration.ofDays(10)));
        when(connections.findById(9L)).thenReturn(Optional.empty());

        assertThat(sut.refresh(refresh, new RefreshConnection("axr_x", CLIENT))).isEmpty();
        verify(tokens, never()).consume(any(), any());
    }

    private static AgentConnectionToken code(Instant expiresAt) {

        return AgentConnectionToken.builder().id(21L).organizationId("default").connectionId(9L)
                .kind(AgentConnectionTokenKind.CODE).tokenHash("h").expiresAt(expiresAt).build();
    }

    private static AgentConnectionToken refreshToken(Instant consumedAt, Instant expiresAt) {

        return AgentConnectionToken.builder().id(22L).organizationId("default").connectionId(9L)
                .kind(AgentConnectionTokenKind.REFRESH).tokenHash("h").consumedAt(consumedAt).expiresAt(expiresAt)
                .build();
    }

    private static AgentConnection pendingConnection() {

        return AgentConnection.builder().id(9L).organizationId("default").userId("u-1").clientId(CLIENT)
                .clientName("Claude Code").clientHost("claude.ai").scopes("read").resource("https://axonia.test/mcp")
                .redirectUri(REDIRECT).codeChallenge(CHALLENGE).endsAt(NOW.plus(Duration.ofDays(180))).build();
    }

    private static AgentConnection activeConnection(Long id) {
        return pendingConnection().toBuilder().id(id).activatedAt(NOW.minusSeconds(60)).build();
    }
}
