package dev.axonia.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.NotFoundException;
import dev.axonia.TokenHasher;
import dev.axonia.ValidationException;
import dev.axonia.identity.pat.MintPersonalAccessToken;
import dev.axonia.identity.pat.UpdatePersonalAccessToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class PersonalAccessTokenAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-07T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    PersonalAccessTokenRepository repository;

    PersonalAccessTokenAdapter sut;

    @BeforeEach
    void setUp() {
        sut = new PersonalAccessTokenAdapter(repository, CLOCK);
    }

    @Test
    void authenticate_returnsPrincipalWithScopes_forActiveToken() {
        var hash = TokenHasher.sha256Hex("axp_active");
        when(repository.findByTokenHash(hash)).thenReturn(Optional.of(PersonalAccessTokenEntity.builder().id(5L)
                .userId("u-1").organizationId("default").scopes("read,topic:propose").build()));

        var principal = sut.authenticate("axp_active");

        assertThat(principal).isPresent();
        assertThat(principal.get().userId()).isEqualTo("u-1");
        assertThat(principal.get().organizationId()).isEqualTo("default");
        assertThat(principal.get().tokenId()).isEqualTo(5L);
        assertThat(principal.get().scopes()).containsExactlyInAnyOrder("read", "topic:propose");
    }

    @Test
    void authenticate_emptyForRevokedOrExpired() {
        when(repository.findByTokenHash(TokenHasher.sha256Hex("axp_revoked")))
                .thenReturn(Optional.of(PersonalAccessTokenEntity.builder().userId("u").organizationId("o")
                        .revokedAt(NOW.minusSeconds(1)).build()));
        when(repository.findByTokenHash(TokenHasher.sha256Hex("axp_expired"))).thenReturn(Optional
                .of(PersonalAccessTokenEntity.builder().userId("u").organizationId("o").expiresAt(NOW).build()));

        assertThat(sut.authenticate("axp_revoked")).isEmpty();
        assertThat(sut.authenticate("axp_expired")).isEmpty(); // expiresAt == now is not after now => expired
    }

    @Test
    void authenticate_isPureRead() {
        when(repository.findByTokenHash(ArgumentMatchers.anyString())).thenReturn(
                Optional.of(PersonalAccessTokenEntity.builder().userId("u").organizationId("o").scopes("").build()));

        sut.authenticate("axp_read");

        verify(repository, never()).save(ArgumentMatchers.any());
    }

    @Test
    void mint_persistsHashWithScopes_andReturnsAxpPlaintextOnce() {
        var plaintext = sut.mint(new MintPersonalAccessToken("u-1", "default", "cli",
                Set.of("read", "library:contribute"), NOW.plusSeconds(3600)));

        assertThat(plaintext).startsWith("axp_");
        var captor = ArgumentCaptor.forClass(PersonalAccessTokenEntity.class);
        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(TokenHasher.sha256Hex(plaintext));
        assertThat(saved.getTokenHash()).isNotEqualTo(plaintext);
        assertThat(saved.getUserId()).isEqualTo("u-1");
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(saved.getScopes().split(",")).containsExactlyInAnyOrder("read", "library:contribute");
    }

    @Test
    void list_mapsToSummariesWithDerivedStatus() {
        when(repository.findByOrganizationIdAndUserIdOrderByCreatedAtDesc("default", "u-1")).thenReturn(java.util.List
                .of(PersonalAccessTokenEntity.builder().id(1L).name("active").scopes("read").createdAt(NOW).build(),
                        PersonalAccessTokenEntity.builder().id(2L).name("revoked").scopes("read")
                                .revokedAt(NOW.minusSeconds(1)).createdAt(NOW).build(),
                        PersonalAccessTokenEntity.builder().id(3L).name("expired").scopes("read")
                                .expiresAt(NOW.minusSeconds(1)).createdAt(NOW).build()));

        var summaries = sut.list("u-1", "default");

        assertThat(summaries).extracting(s -> s.status()).containsExactly("ACTIVE", "REVOKED", "EXPIRED");
        assertThat(summaries.get(0).scopes()).containsExactly("read");
    }

    @Test
    void revoke_setsRevokedAt_forOwnedToken() {
        when(repository.findByOrganizationIdAndUserIdAndId("default", "u-1", 7L)).thenReturn(Optional
                .of(PersonalAccessTokenEntity.builder().id(7L).userId("u-1").organizationId("default").build()));

        sut.revoke("u-1", "default", 7L);

        var captor = ArgumentCaptor.forClass(PersonalAccessTokenEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void revoke_notOwned_throwsNotFound_andDoesNotMutate() {
        when(repository.findByOrganizationIdAndUserIdAndId("default", "u-1", 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.revoke("u-1", "default", 99L)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).save(ArgumentMatchers.any());
    }

    @Test
    void recordUsage_touchesById_andSwallowsFailures() {
        sut.recordUsage("default", 7L);
        verify(repository).touchLastUsed("default", 7L, NOW, NOW.minus(Duration.ofMinutes(1)));

        doThrow(new RuntimeException("db down")).when(repository).touchLastUsed(ArgumentMatchers.anyString(),
                ArgumentMatchers.anyLong(), ArgumentMatchers.any(), ArgumentMatchers.any());
        assertThatCode(() -> sut.recordUsage("default", 8L)).doesNotThrowAnyException();
    }

    private static PersonalAccessTokenEntity existingToken(String scopes) {
        return PersonalAccessTokenEntity.builder().id(7L).userId("u-1").organizationId("default")
                .tokenHash(TokenHasher.sha256Hex("axp_plain")).name("cli").scopes(scopes).build();
    }

    private static Set<String> orderedScopes(String... scopes) {
        return new LinkedHashSet<>(List.of(scopes));
    }

    @Test
    void update_replacesNameAndScopes_withoutTouchingTheSecret() {
        when(repository.findByOrganizationIdAndUserIdAndId("default", "u-1", 7L))
                .thenReturn(Optional.of(existingToken("read")));

        sut.update(
                new UpdatePersonalAccessToken("u-1", "default", 7L, "claude", orderedScopes("read", "topic:propose")));

        var saved = ArgumentCaptor.forClass(PersonalAccessTokenEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("claude");
        assertThat(saved.getValue().getScopes()).isEqualTo("read,topic:propose");
        assertThat(saved.getValue().getTokenHash()).isEqualTo(TokenHasher.sha256Hex("axp_plain"));
    }

    @Test
    void update_replacesTheScopeSetWholesale_ratherThanMergingIntoIt() {
        when(repository.findByOrganizationIdAndUserIdAndId("default", "u-1", 7L))
                .thenReturn(Optional.of(existingToken("read,members:read")));

        sut.update(new UpdatePersonalAccessToken("u-1", "default", 7L, "cli", orderedScopes("read")));

        var saved = ArgumentCaptor.forClass(PersonalAccessTokenEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getScopes()).isEqualTo("read");
    }

    @Test
    void update_refusesARevokedToken() {
        when(repository.findByOrganizationIdAndUserIdAndId("default", "u-1", 7L))
                .thenReturn(Optional.of(existingToken("read").toBuilder().revokedAt(NOW.minusSeconds(60)).build()));

        assertThatThrownBy(
                () -> sut.update(new UpdatePersonalAccessToken("u-1", "default", 7L, "cli", orderedScopes("read"))))
                .isInstanceOf(ValidationException.class);
        verify(repository, never()).save(ArgumentMatchers.any());
    }

    @Test
    void update_refusesAnExpiredToken() {
        when(repository.findByOrganizationIdAndUserIdAndId("default", "u-1", 7L))
                .thenReturn(Optional.of(existingToken("read").toBuilder().expiresAt(NOW.minusSeconds(1)).build()));

        assertThatThrownBy(
                () -> sut.update(new UpdatePersonalAccessToken("u-1", "default", 7L, "cli", orderedScopes("read"))))
                .isInstanceOf(ValidationException.class);
        verify(repository, never()).save(ArgumentMatchers.any());
    }

    @Test
    void update_isNotFoundWhenTheTokenIsNotTheCallers() {
        when(repository.findByOrganizationIdAndUserIdAndId("default", "u-1", 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> sut.update(new UpdatePersonalAccessToken("u-1", "default", 7L, "cli", orderedScopes("read"))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_leavesTheSamePlaintextAuthenticating_withTheNewScopes() {
        when(repository.findByOrganizationIdAndUserIdAndId("default", "u-1", 7L))
                .thenReturn(Optional.of(existingToken("read")));

        sut.update(new UpdatePersonalAccessToken("u-1", "default", 7L, "cli", orderedScopes("read", "session:signup")));

        var saved = ArgumentCaptor.forClass(PersonalAccessTokenEntity.class);
        verify(repository).save(saved.capture());
        when(repository.findByTokenHash(TokenHasher.sha256Hex("axp_plain"))).thenReturn(Optional.of(saved.getValue()));

        var principal = sut.authenticate("axp_plain");

        assertThat(principal).isPresent();
        assertThat(principal.get().scopes()).containsExactlyInAnyOrder("read", "session:signup");
    }
}
