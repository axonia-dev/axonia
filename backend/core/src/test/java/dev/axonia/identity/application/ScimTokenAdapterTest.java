package dev.axonia.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.application.port.out.ScimTokenStore;
import dev.axonia.identity.domain.ScimToken;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.secret.TokenHasher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class ScimTokenAdapterTest {

    private static final Instant NOW = Instant.parse("2026-06-25T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    ScimTokenStore repository;

    ScimTokenAdapter sut;

    @BeforeEach
    void setUp() {
        sut = new ScimTokenAdapter(repository, CLOCK);
    }

    // ── authenticate ────────────────────────────────────────────────────────────

    @Test
    void authenticate_returnsPrincipal_forActiveToken() {

        // given
        var hash = TokenHasher.sha256Hex("secret-xyz");
        when(repository.findByTokenHash(hash))
                .thenReturn(Optional.of(ScimToken.builder().organizationId("default").build()));

        // when
        var principal = sut.authenticate("secret-xyz");

        // then
        assertThat(principal).isPresent();
        assertThat(principal.get().organizationId()).isEqualTo("default");
    }

    @Test
    void authenticate_emptyForUnknownToken() {

        // given
        when(repository.findByTokenHash(ArgumentMatchers.anyString())).thenReturn(Optional.empty());

        // then
        assertThat(sut.authenticate("nope")).isEmpty();
    }

    @Test
    void authenticate_emptyForRevokedToken() {

        // given
        var hash = TokenHasher.sha256Hex("secret-revoked");
        when(repository.findByTokenHash(hash)).thenReturn(
                Optional.of(ScimToken.builder().organizationId("t1").revokedAt(NOW.minusSeconds(60)).build()));

        // then
        assertThat(sut.authenticate("secret-revoked")).isEmpty();
    }

    @Test
    void authenticate_emptyForExpiredToken() {

        // given
        var hash = TokenHasher.sha256Hex("secret-expired");
        when(repository.findByTokenHash(hash)).thenReturn(
                Optional.of(ScimToken.builder().organizationId("t1").expiresAt(NOW.minusSeconds(1)).build()));

        // then
        assertThat(sut.authenticate("secret-expired")).isEmpty();
    }

    @Test
    void authenticate_emptyWhenExpiresAtEqualsNow() {

        // given — boundary: expiresAt == now is NOT after now, so it is expired
        var hash = TokenHasher.sha256Hex("secret-boundary");
        when(repository.findByTokenHash(hash))
                .thenReturn(Optional.of(ScimToken.builder().organizationId("t1").expiresAt(NOW).build()));

        // then
        assertThat(sut.authenticate("secret-boundary")).isEmpty();
    }

    @Test
    void authenticate_returnsPrincipal_forFutureExpiry() {

        // given
        var hash = TokenHasher.sha256Hex("secret-future");
        when(repository.findByTokenHash(hash)).thenReturn(
                Optional.of(ScimToken.builder().organizationId("t2").expiresAt(NOW.plusSeconds(3600)).build()));

        // when
        var principal = sut.authenticate("secret-future");

        // then
        assertThat(principal).isPresent();
        assertThat(principal.get().organizationId()).isEqualTo("t2");
    }

    @Test
    void authenticate_isPureRead_doesNotWrite() {

        // given
        var hash = TokenHasher.sha256Hex("secret-read");
        when(repository.findByTokenHash(hash))
                .thenReturn(Optional.of(ScimToken.builder().organizationId("t1").build()));

        // when
        sut.authenticate("secret-read");

        // then - authenticate is a pure read
        verify(repository, never()).save(ArgumentMatchers.any());
    }

    // ── generate (additive) ─────────────────────────────────────────────────────

    @Test
    void generate_isAdditive_persistsDistinctRowsWithoutDeleting() {

        // when
        var first = sut.generate("default", "label-1", null);
        var second = sut.generate("default", "label-2", NOW.plusSeconds(3600));

        // then — both tokens minted; nothing deleted (additive)
        assertThat(first).startsWith("scim_");
        assertThat(second).startsWith("scim_");
        assertThat(first).isNotEqualTo(second);
        verify(repository, times(2)).save(ArgumentMatchers.any());
        verifyNoMoreInteractions(repository);

        var captor = ArgumentCaptor.forClass(ScimToken.class);
        verify(repository, times(2)).save(captor.capture());
        var saved = captor.getAllValues();
        assertThat(saved.get(0).tokenHash()).isNotEqualTo(saved.get(1).tokenHash());
        assertThat(saved.get(0).expiresAt()).isNull();
        assertThat(saved.get(1).expiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(saved.get(0).label()).isEqualTo("label-1");
    }

    // ── revoke (per-id, org-scoped) ─────────────────────────────────────────────

    @Test
    void revoke_setsRevokedAt_forOwningOrg() {

        // given
        var entity = ScimToken.builder().id(7L).organizationId("default").build();
        when(repository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));

        // when
        sut.revoke("default", 7L);

        // then
        var captor = ArgumentCaptor.forClass(ScimToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().revokedAt()).isEqualTo(NOW);
        assertThat(captor.getValue().id()).isEqualTo(7L);
    }

    @Test
    void revoke_foreignOrgId_throwsNotFound_andDoesNotMutate() {

        // given — RLS-safe finder returns empty for an id the caller's org does not own
        when(repository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> sut.revoke("default", 99L)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).save(ArgumentMatchers.any());
    }

    // ── recordUsage (advance last_used_at after the Organization GUC) ────────────

    @Test
    void recordUsage_touchesLastUsed_withHashedToken_andClockInstant() {

        // when
        sut.recordUsage("default", "secret-usage");

        // then — hashes the bearer token and stamps the clock instant; org-scoped
        verify(repository).touchLastUsed("default", TokenHasher.sha256Hex("secret-usage"), NOW);
    }

    @Test
    void recordUsage_swallowsRepositoryException() {

        // given — a failed usage write must never fail the SCIM request
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(repository)
                .touchLastUsed(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any());

        // then — no exception propagates
        org.assertj.core.api.Assertions.assertThatCode(() -> sut.recordUsage("default", "secret-boom"))
                .doesNotThrowAnyException();
        verify(repository).touchLastUsed("default", TokenHasher.sha256Hex("secret-boom"), NOW);
    }
}
