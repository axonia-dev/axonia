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

import dev.axonia.identity.domain.ScimToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcScimTokenStoreTest {

    private static final ScimTokenEntity ENTITY = ScimTokenEntity.builder().id(10L).organizationId("organizationId-1")
            .tokenHash("tokenHash-2").label("label-3").lastUsedAt(Instant.parse("2026-05-05T10:00:00Z"))
            .revokedAt(Instant.parse("2026-06-06T10:00:00Z")).expiresAt(Instant.parse("2026-07-07T10:00:00Z"))
            .createdAt(Instant.parse("2026-08-08T10:00:00Z")).updatedAt(Instant.parse("2026-09-09T10:00:00Z"))
            .version(19L).build();

    private static final ScimToken DOMAIN = JdbcScimTokenStore.toScimToken(ENTITY);

    @Mock
    private ScimTokenRepository scimTokenRepository;

    @InjectMocks
    private JdbcScimTokenStore store;

    @Test
    void mapsEveryColumnBothWays() {

        assertThat(DOMAIN).usingRecursiveComparison().isEqualTo(ENTITY);
        assertThat(JdbcScimTokenStore.toEntity(DOMAIN)).usingRecursiveComparison().isEqualTo(ENTITY);
    }

    @Test
    void findByTokenHashMapsTheRowItFinds() {

        when(scimTokenRepository.findByTokenHash("tokenHash-0")).thenReturn(Optional.of(ENTITY));

        assertThat(store.findByTokenHash("tokenHash-0")).contains(DOMAIN);
    }

    @Test
    void findByOrganizationIdMapsEveryRowItFinds() {

        when(scimTokenRepository.findByOrganizationId("organizationId-0")).thenReturn(List.of(ENTITY));

        assertThat(store.findByOrganizationId("organizationId-0")).containsExactly(DOMAIN);
    }

    @Test
    void findByOrganizationIdAndIdMapsTheRowItFinds() {

        when(scimTokenRepository.findByOrganizationIdAndId("organizationId-0", 11L)).thenReturn(Optional.of(ENTITY));

        assertThat(store.findByOrganizationIdAndId("organizationId-0", 11L)).contains(DOMAIN);
    }

    @Test
    void saveWritesTheMappedRowAndAnswersWhatWasStored() {

        when(scimTokenRepository.save(any())).thenReturn(ENTITY);

        var actual = store.save(DOMAIN);

        var written = ArgumentCaptor.forClass(ScimTokenEntity.class);
        verify(scimTokenRepository).save(written.capture());
        assertThat(written.getValue()).usingRecursiveComparison().isEqualTo(ENTITY);
        assertThat(actual).isEqualTo(DOMAIN);
    }

    @Test
    void touchLastUsedDelegatesToTheRepository() {

        store.touchLastUsed("organizationId-0", "tokenHash-1", Instant.parse("2026-03-03T10:00:00Z"));

        verify(scimTokenRepository).touchLastUsed("organizationId-0", "tokenHash-1",
                Instant.parse("2026-03-03T10:00:00Z"));
    }
}
