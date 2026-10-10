package dev.axonia.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.axonia.identity.api.connection.AuthorizeConnection;
import dev.axonia.identity.api.connection.ConnectionPrincipal;
import dev.axonia.identity.api.connection.ConnectionSummary;
import dev.axonia.identity.api.connection.IssuedTokens;
import dev.axonia.identity.api.connection.RedeemCode;
import dev.axonia.identity.api.connection.RefreshConnection;
import dev.axonia.identity.application.port.out.AgentConnectionTokenStore;
import dev.axonia.identity.domain.AgentConnectionToken;
import dev.axonia.identity.domain.AgentConnectionTokenKind;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.secret.TokenHasher;
import dev.axonia.shared.api.tenancy.OrganizationBoundAuthentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class AgentConnectionAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    AgentConnectionTokenStore tokens;

    @Mock
    AgentConnectionGrants grants;

    @Mock
    AgentConnectionLedger ledger;

    AgentConnectionAdapter sut;

    @BeforeEach
    void setUp() {
        sut = new AgentConnectionAdapter(tokens, grants, ledger, CLOCK);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticate_looksUpOnlyAccessTokens() {

        // given
        var refresh = token(AgentConnectionTokenKind.REFRESH, NOW.plusSeconds(60));
        when(tokens.findByTokenHash(TokenHasher.sha256Hex("axa_mislabelled"))).thenReturn(Optional.of(refresh));

        // when / then
        assertThat(sut.authenticate("axa_mislabelled")).isEmpty();
        assertThat(sut.authenticate("axp_personal")).isEmpty();
        verify(ledger, never()).principal(any());
    }

    @Test
    void authenticate_refusesAnExpiredAccessToken() {

        // given: a live principal behind it, so only the expiry check can refuse
        when(tokens.findByTokenHash(TokenHasher.sha256Hex("axa_old")))
                .thenReturn(Optional.of(token(AgentConnectionTokenKind.ACCESS, NOW)));
        lenient().when(ledger.principal(any())).thenReturn(
                Optional.of(new ConnectionPrincipal("u-1", "acme", Set.of("read"), 9L, "https://claude.ai/c")));

        // when / then
        assertThat(sut.authenticate("axa_old")).isEmpty();
        verify(ledger, never()).principal(any());
    }

    @Test
    void authenticate_readsThePrincipalInsideTheTokensOrganization() {

        // given
        var access = token(AgentConnectionTokenKind.ACCESS, NOW.plusSeconds(60));
        when(tokens.findByTokenHash(TokenHasher.sha256Hex("axa_ok"))).thenReturn(Optional.of(access));
        when(ledger.principal(access)).thenAnswer(invocation -> {
            assertScopedTo("acme");
            return Optional.of(new ConnectionPrincipal("u-1", "acme", Set.of("read"), 9L, "https://claude.ai/c"));
        });

        // when
        var actual = sut.authenticate("axa_ok");

        // then
        assertThat(actual).isPresent();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void redeem_andRefresh_routeByPrefix() {

        var code = token(AgentConnectionTokenKind.CODE, NOW.plusSeconds(60));
        when(tokens.findByTokenHash(TokenHasher.sha256Hex("axc_1"))).thenReturn(Optional.of(code));
        var command = new RedeemCode("axc_1", "c", "r", "v");
        when(grants.redeem(code, command)).thenReturn(Optional.empty());

        sut.redeem(command);
        assertThat(sut.refresh(new RefreshConnection("axc_1", "c"))).isEmpty();

        verify(grants).redeem(code, command);
        verify(grants, never()).refresh(any(), any());
    }

    @Test
    void revokeToken_revokesTheConnectionBehindAnyKind_andIgnoresUnknownTokens() {

        when(tokens.findByTokenHash(TokenHasher.sha256Hex("axr_1")))
                .thenReturn(Optional.of(token(AgentConnectionTokenKind.REFRESH, NOW.plusSeconds(60))));

        sut.revokeToken("axr_1");
        sut.revokeToken("axr_unknown");
        sut.revokeToken("nonsense");

        verify(ledger).revokeConnection(9L);
    }

    @Test
    void authenticate_refusesAMissingToken_withoutALookup() {

        assertThat(sut.authenticate(null)).isEmpty();

        verifyNoInteractions(tokens, ledger);
    }

    @Test
    void authorize_returnsTheCodeTheGrantsIssue() {

        // given
        var command = AuthorizeConnection.builder().userId("u-1").organizationId("acme").clientId("c").build();
        when(grants.authorize(command)).thenReturn("axc_issued");

        // when / then
        assertThat(sut.authorize(command)).isEqualTo("axc_issued");
    }

    @Test
    void refresh_rotatesInsideTheTokensOrganization() {

        // given
        var refresh = token(AgentConnectionTokenKind.REFRESH, NOW.plusSeconds(60));
        when(tokens.findByTokenHash(TokenHasher.sha256Hex("axr_ok"))).thenReturn(Optional.of(refresh));
        var command = new RefreshConnection("axr_ok", "c");
        var rotated = new IssuedTokens("axa_new", "axr_new", Duration.ofHours(1), Set.of("read"));
        when(grants.refresh(refresh, command)).thenAnswer(invocation -> {
            assertScopedTo("acme");
            return Optional.of(rotated);
        });

        // when
        var actual = sut.refresh(command);

        // then
        assertThat(actual).contains(rotated);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void list_answersWithTheLedgersSummaries() {

        // given
        var summary = new ConnectionSummary(9L, "Claude Code", "claude.ai", Set.of("read"), NOW, null,
                NOW.plus(Duration.ofDays(180)), "ACTIVE");
        when(ledger.list("u-1", "acme")).thenReturn(List.of(summary));

        // when / then
        assertThat(sut.list("u-1", "acme")).containsExactly(summary);
    }

    @Test
    void revoke_revokesTheCallersOwnConnection() {

        sut.revoke("u-1", "acme", 9L);

        verify(ledger).revoke("u-1", "acme", 9L);
    }

    @Test
    void revoke_letsANotFoundThrough() {

        doThrow(new NotFoundException("Agent connection not found: 9")).when(ledger).revoke("u-2", "acme", 9L);

        assertThatThrownBy(() -> sut.revoke("u-2", "acme", 9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void recordUsage_stampsTheConnection() {

        sut.recordUsage("acme", 9L);

        verify(ledger).recordUsage("acme", 9L);
    }

    @Test
    void sweep_reportsHowManyRowsTheLedgerDeleted() {

        when(ledger.sweep("acme")).thenReturn(4);

        assertThat(sut.sweep("acme")).isEqualTo(4);
    }

    private static void assertScopedTo(String organizationId) {

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(OrganizationBoundAuthentication.class)
                .extracting(a -> ((OrganizationBoundAuthentication) a).organizationId()).isEqualTo(organizationId);
    }

    // A grant that loses a race answers invalid_grant, never a server error

    @ParameterizedTest
    @MethodSource("races")
    void redeem_answersEmptyWhenAConcurrentChangeWins(RuntimeException race) {

        // given
        var code = token(AgentConnectionTokenKind.CODE, NOW.plusSeconds(60));
        when(tokens.findByTokenHash(TokenHasher.sha256Hex("axc_1"))).thenReturn(Optional.of(code));
        var command = new RedeemCode("axc_1", "c", "r", "v");
        when(grants.redeem(code, command)).thenThrow(race);

        // when / then
        assertThat(sut.redeem(command)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("races")
    void refresh_answersEmptyWhenAConcurrentChangeWins(RuntimeException race) {

        // given
        var refresh = token(AgentConnectionTokenKind.REFRESH, NOW.plusSeconds(60));
        when(tokens.findByTokenHash(TokenHasher.sha256Hex("axr_1"))).thenReturn(Optional.of(refresh));
        var command = new RefreshConnection("axr_1", "c");
        when(grants.refresh(refresh, command)).thenThrow(race);

        // when / then
        assertThat(sut.refresh(command)).isEmpty();
    }

    @Test
    void redeem_letsAnUnrelatedFailureThrough() {

        var code = token(AgentConnectionTokenKind.CODE, NOW.plusSeconds(60));
        when(tokens.findByTokenHash(TokenHasher.sha256Hex("axc_1"))).thenReturn(Optional.of(code));
        when(grants.redeem(any(), any())).thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(() -> sut.redeem(new RedeemCode("axc_1", "c", "r", "v")))
                .isInstanceOf(IllegalStateException.class);
    }

    static Stream<RuntimeException> races() {

        return Stream.of(new DuplicateKeyException("uq_agent_connection_active"),
                new OptimisticLockingFailureException("stale version"),
                new DataIntegrityViolationException("fk_agent_connection_token_connection"));
    }

    private static AgentConnectionToken token(AgentConnectionTokenKind kind, Instant expiresAt) {

        return AgentConnectionToken.builder().id(30L).organizationId("acme").connectionId(9L).kind(kind).tokenHash("h")
                .expiresAt(expiresAt).build();
    }
}
