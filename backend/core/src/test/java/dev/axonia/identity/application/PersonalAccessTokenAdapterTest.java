package dev.axonia.identity.application;

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

import dev.axonia.identity.api.pat.MintPersonalAccessToken;
import dev.axonia.identity.api.pat.UpdatePersonalAccessToken;
import dev.axonia.identity.application.port.out.PersonalAccessTokenStore;
import dev.axonia.identity.domain.PersonalAccessToken;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;
import dev.axonia.shared.api.secret.TokenHasher;

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
    PersonalAccessTokenStore repository;

    PersonalAccessTokenAdapter sut;

    @BeforeEach
    void setUp() {
        sut = new PersonalAccessTokenAdapter(repository, CLOCK);
    }

    @Test
    void authenticate_returnsPrincipalWithScopes_forActiveToken() {
        var hash = TokenHasher.sha256Hex("axp_active");
        when(repository.findByTokenHash(hash)).thenReturn(Optional.of(PersonalAccessToken.builder().id(5L).userId("u-1")
                .organizationId("default").scopes("read,topic:propose").build()));

        var principal = sut.authenticate("axp_active");

        assertThat(principal).isPresent();
        assertThat(principal.get().userId()).isEqualTo("u-1");
        assertThat(principal.get().organizationId()).isEqualTo("default");
        assertThat(principal.get().tokenId()).isEqualTo(5L);
        assertThat(principal.get().scopes()).containsExactlyInAnyOrder("read", "topic:propose");
    }

    @Test
    void authenticate_emptyForRevokedOrExpired() {
        when(repository.findByTokenHash(TokenHasher.sha256Hex("axp_revoked"))).thenReturn(Optional.of(
                PersonalAccessToken.builder().userId("u").organizationId("o").revokedAt(NOW.minusSeconds(1)).build()));
        when(repository.findByTokenHash(TokenHasher.sha256Hex("axp_expired"))).thenReturn(
                Optional.of(PersonalAccessToken.builder().userId("u").organizationId("o").expiresAt(NOW).build()));

        assertThat(sut.authenticate("axp_revoked")).isEmpty();
        assertThat(sut.authenticate("axp_expired")).isEmpty(); // expiresAt == now is not after now => expired
    }

    @Test
    void authenticate_isPureRead() {
        when(repository.findByTokenHash(ArgumentMatchers.anyString())).thenReturn(
                Optional.of(PersonalAccessToken.builder().userId("u").organizationId("o").scopes("").build()));

        sut.authenticate("axp_read");

        verify(repository, never()).save(ArgumentMatchers.any());
    }

    @Test
    void mint_persistsHashWithScopes_andReturnsAxpPlaintextOnce() {
        var plaintext = sut.mint(new MintPersonalAccessToken("u-1", "default", "cli",
                Set.of("read", "library:contribute"), NOW.plusSeconds(3600)));

        assertThat(plaintext).startsWith("axp_");
        var captor = ArgumentCaptor.forClass(PersonalAccessToken.class);
        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.tokenHash()).isEqualTo(TokenHasher.sha256Hex(plaintext));
        assertThat(saved.tokenHash()).isNotEqualTo(plaintext);
        assertThat(saved.userId()).isEqualTo("u-1");
        assertThat(saved.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(saved.scopes().split(",")).containsExactlyInAnyOrder("read", "library:contribute");
    }

    @Test
    void list_mapsToSummariesWithDerivedStatus() {
        when(repository.findByOrganizationIdAndUserIdOrderByCreatedAtDesc("default", "u-1")).thenReturn(java.util.List
                .of(PersonalAccessToken.builder().id(1L).name("active").scopes("read").createdAt(NOW).build(),
                        PersonalAccessToken.builder().id(2L).name("revoked").scopes("read")
                                .revokedAt(NOW.minusSeconds(1)).createdAt(NOW).build(),
                        PersonalAccessToken.builder().id(3L).name("expired").scopes("read")
                                .expiresAt(NOW.minusSeconds(1)).createdAt(NOW).build()));

        var summaries = sut.list("u-1", "default");

        assertThat(summaries).extracting(s -> s.status()).containsExactly("ACTIVE", "REVOKED", "EXPIRED");
        assertThat(summaries.get(0).scopes()).containsExactly("read");
    }

    @Test
    void revoke_setsRevokedAt_forOwnedToken() {
        when(repository.findByOrganizationIdAndUserIdAndId("default", "u-1", 7L)).thenReturn(
                Optional.of(PersonalAccessToken.builder().id(7L).userId("u-1").organizationId("default").build()));

        sut.revoke("u-1", "default", 7L);

        var captor = ArgumentCaptor.forClass(PersonalAccessToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().revokedAt()).isEqualTo(NOW);
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

    private static PersonalAccessToken existingToken(String scopes) {
        return PersonalAccessToken.builder().id(7L).userId("u-1").organizationId("default")
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

        var saved = ArgumentCaptor.forClass(PersonalAccessToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().name()).isEqualTo("claude");
        assertThat(saved.getValue().scopes()).isEqualTo("read,topic:propose");
        assertThat(saved.getValue().tokenHash()).isEqualTo(TokenHasher.sha256Hex("axp_plain"));
    }

    @Test
    void update_replacesTheScopeSetWholesale_ratherThanMergingIntoIt() {
        when(repository.findByOrganizationIdAndUserIdAndId("default", "u-1", 7L))
                .thenReturn(Optional.of(existingToken("read,members:read")));

        sut.update(new UpdatePersonalAccessToken("u-1", "default", 7L, "cli", orderedScopes("read")));

        var saved = ArgumentCaptor.forClass(PersonalAccessToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().scopes()).isEqualTo("read");
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

        var saved = ArgumentCaptor.forClass(PersonalAccessToken.class);
        verify(repository).save(saved.capture());
        when(repository.findByTokenHash(TokenHasher.sha256Hex("axp_plain"))).thenReturn(Optional.of(saved.getValue()));

        var principal = sut.authenticate("axp_plain");

        assertThat(principal).isPresent();
        assertThat(principal.get().scopes()).containsExactlyInAnyOrder("read", "session:signup");
    }
}
