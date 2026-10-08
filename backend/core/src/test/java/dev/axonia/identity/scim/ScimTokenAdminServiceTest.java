package dev.axonia.identity.scim;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.ForbiddenException;
import dev.axonia.identity.ScimTokenGate;
import dev.axonia.identity.internal.ScimTokenEntity;
import dev.axonia.identity.internal.ScimTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class ScimTokenAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-25T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    ScimTokens scimTokens;

    @Mock
    ScimTokenGate gate;

    @Mock
    ScimTokenRepository tokenRepository;

    ScimTokenAdminService sut;

    @BeforeEach
    void setUp() {
        sut = new ScimTokenAdminService(scimTokens, gate, tokenRepository, CLOCK);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── generate ──────────────────────────────────────────────────────────────

    @Test
    void generate_requiresWrite_thenDelegatesToScimTokens() {

        // given
        withJwt("u1", "default");
        var expiresAt = NOW.plusSeconds(3600);
        when(scimTokens.generate("default", "label", expiresAt)).thenReturn("scim_tok");

        // when
        sut.generate("label", expiresAt);

        // then
        var ordered = inOrder(gate, scimTokens);
        ordered.verify(gate).requireWrite();
        ordered.verify(scimTokens).generate("default", "label", expiresAt);
    }

    @Test
    void generate_nullExpiresAt_delegatesNull() {

        // given
        withJwt("u1", "default");
        when(scimTokens.generate(eq("default"), eq("label"), isNull())).thenReturn("scim_abc");

        // then
        assertThat(sut.generate("label", null)).isEqualTo("scim_abc");
        verify(scimTokens).generate("default", "label", null);
    }

    @Test
    void generate_forbidden_throwsAndDoesNotDelegate() {

        // given
        doThrow(new ForbiddenException("no")).when(gate).requireWrite();

        // then
        assertThatThrownBy(() -> sut.generate("label", null)).isInstanceOf(ForbiddenException.class);
        verify(scimTokens, never()).generate(anyString(), anyString(), any());
    }

    // ── revoke ────────────────────────────────────────────────────────────────

    @Test
    void revoke_requiresWrite_thenDelegatesById() {

        // given
        withJwt("u1", "default");

        // when
        sut.revoke(42L);

        // then
        var ordered = inOrder(gate, scimTokens);
        ordered.verify(gate).requireWrite();
        ordered.verify(scimTokens).revoke("default", 42L);
    }

    @Test
    void revoke_forbidden_throwsAndDoesNotDelegate() {

        // given
        doThrow(new ForbiddenException("no")).when(gate).requireWrite();

        // then
        assertThatThrownBy(() -> sut.revoke(42L)).isInstanceOf(ForbiddenException.class);
        verify(scimTokens, never()).revoke(anyString(), any());
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_requiresRead_andReturnsMetadata_withDerivedStatusActive() {

        // given
        withJwt("u1", "default");
        var entity = ScimTokenEntity.builder().id(5L).organizationId("default").label("my-label")
                .lastUsedAt(NOW.minusSeconds(10)).expiresAt(NOW.plusSeconds(3600)).createdAt(NOW.minusSeconds(100))
                .tokenHash("SHOULD_NOT_APPEAR").build();
        when(tokenRepository.findByOrganizationId("default")).thenReturn(List.of(entity));

        // when
        var result = sut.list();

        // then
        verify(gate).requireRead();
        assertThat(result).hasSize(1);
        var view = result.get(0);
        assertThat(view.id()).isEqualTo(5L);
        assertThat(view.label()).isEqualTo("my-label");
        assertThat(view.lastUsedAt()).isEqualTo(NOW.minusSeconds(10));
        assertThat(view.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(view.createdAt()).isEqualTo(NOW.minusSeconds(100));
        assertThat(view.status()).isEqualTo("ACTIVE");
    }

    @Test
    void list_statusActive_whenNeverExpires() {

        // given
        withJwt("u1", "default");
        when(tokenRepository.findByOrganizationId("default"))
                .thenReturn(List.of(ScimTokenEntity.builder().organizationId("default").build()));

        // then
        assertThat(sut.list().get(0).status()).isEqualTo("ACTIVE");
    }

    @Test
    void list_statusRevoked_whenRevokedAtSet() {

        // given
        withJwt("u1", "default");
        when(tokenRepository.findByOrganizationId("default")).thenReturn(
                List.of(ScimTokenEntity.builder().organizationId("default").revokedAt(NOW.minusSeconds(5)).build()));

        // then
        assertThat(sut.list().get(0).status()).isEqualTo("REVOKED");
    }

    @Test
    void list_statusExpired_whenExpiresAtInPast() {

        // given
        withJwt("u1", "default");
        when(tokenRepository.findByOrganizationId("default")).thenReturn(
                List.of(ScimTokenEntity.builder().organizationId("default").expiresAt(NOW.minusSeconds(1)).build()));

        // then
        assertThat(sut.list().get(0).status()).isEqualTo("EXPIRED");
    }

    @Test
    void list_statusRevoked_takesPrecedenceOverExpired() {

        // given — both revoked and expired: revoked wins
        withJwt("u1", "default");
        when(tokenRepository.findByOrganizationId("default")).thenReturn(List.of(ScimTokenEntity.builder()
                .organizationId("default").revokedAt(NOW.minusSeconds(5)).expiresAt(NOW.minusSeconds(1)).build()));

        // then
        assertThat(sut.list().get(0).status()).isEqualTo("REVOKED");
    }

    @Test
    void list_neverExposesTokenHash() {

        // given
        withJwt("u1", "default");
        var entity = ScimTokenEntity.builder().organizationId("default").label("label").tokenHash("secret-hash")
                .build();
        when(tokenRepository.findByOrganizationId("default")).thenReturn(List.of(entity));

        // when
        var result = sut.list();

        // then
        // ScimTokenView has no tokenHash field — this asserts no hash leaks via toString
        assertThat(result.get(0).toString()).doesNotContain("secret-hash");
    }

    @Test
    void list_forbidden_throwsAndDoesNotQuery() {

        // given
        doThrow(new ForbiddenException("no")).when(gate).requireRead();

        // then
        assertThatThrownBy(() -> sut.list()).isInstanceOf(ForbiddenException.class);
        verify(tokenRepository, never()).findByOrganizationId(anyString());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void withJwt(String sub, String organization) {
        var jwt = Jwt.withTokenValue("tok").header("alg", "RS256").subject(sub).claim("org", organization).build();
        SecurityContextHolder.setContext(new SecurityContextImpl(new JwtAuthenticationToken(jwt)));
    }
}
