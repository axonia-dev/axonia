package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.identity.domain.PersonalAccessToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcPersonalAccessTokenStoreTest {

    private static final PersonalAccessTokenEntity ENTITY = PersonalAccessTokenEntity.builder().id(10L)
            .organizationId("organizationId-1").userId("userId-2").tokenHash("tokenHash-3").name("name-4")
            .scopes("scopes-5").lastUsedAt(Instant.parse("2026-07-07T10:00:00Z"))
            .revokedAt(Instant.parse("2026-08-08T10:00:00Z")).expiresAt(Instant.parse("2026-09-09T10:00:00Z"))
            .createdAt(Instant.parse("2026-01-01T10:00:00Z")).updatedAt(Instant.parse("2026-02-02T10:00:00Z"))
            .version(21L).build();

    private static final PersonalAccessToken DOMAIN = JdbcPersonalAccessTokenStore.toPersonalAccessToken(ENTITY);

    @Mock
    private PersonalAccessTokenRepository personalAccessTokenRepository;

    @InjectMocks
    private JdbcPersonalAccessTokenStore store;

    @Test
    void mapsEveryColumnBothWays() {

        assertThat(DOMAIN).usingRecursiveComparison().isEqualTo(ENTITY);
        assertThat(JdbcPersonalAccessTokenStore.toEntity(DOMAIN)).usingRecursiveComparison().isEqualTo(ENTITY);
    }

    @Test
    void findByTokenHashMapsTheRowItFinds() {

        when(personalAccessTokenRepository.findByTokenHash("tokenHash-0")).thenReturn(Optional.of(ENTITY));

        assertThat(store.findByTokenHash("tokenHash-0")).contains(DOMAIN);
    }

    @Test
    void findByOrganizationIdAndUserIdOrderByCreatedAtDescMapsEveryRowItFinds() {

        when(personalAccessTokenRepository.findByOrganizationIdAndUserIdOrderByCreatedAtDesc("organizationId-0",
                "userId-1")).thenReturn(List.of(ENTITY));

        assertThat(store.findByOrganizationIdAndUserIdOrderByCreatedAtDesc("organizationId-0", "userId-1"))
                .containsExactly(DOMAIN);
    }

    @Test
    void findByOrganizationIdAndUserIdAndIdMapsTheRowItFinds() {

        when(personalAccessTokenRepository.findByOrganizationIdAndUserIdAndId("organizationId-0", "userId-1", 12L))
                .thenReturn(Optional.of(ENTITY));

        assertThat(store.findByOrganizationIdAndUserIdAndId("organizationId-0", "userId-1", 12L)).contains(DOMAIN);
    }

    @Test
    void saveWritesTheMappedRowAndAnswersWhatWasStored() {

        when(personalAccessTokenRepository.save(any())).thenReturn(ENTITY);

        var actual = store.save(DOMAIN);

        var written = ArgumentCaptor.forClass(PersonalAccessTokenEntity.class);
        verify(personalAccessTokenRepository).save(written.capture());
        assertThat(written.getValue()).usingRecursiveComparison().isEqualTo(ENTITY);
        assertThat(actual).isEqualTo(DOMAIN);
    }

    @Test
    void touchLastUsedDelegatesToTheRepository() {

        store.touchLastUsed("organizationId-0", 11L, Instant.parse("2026-03-03T10:00:00Z"),
                Instant.parse("2026-04-04T10:00:00Z"));

        verify(personalAccessTokenRepository).touchLastUsed("organizationId-0", 11L,
                Instant.parse("2026-03-03T10:00:00Z"), Instant.parse("2026-04-04T10:00:00Z"));
    }
}
