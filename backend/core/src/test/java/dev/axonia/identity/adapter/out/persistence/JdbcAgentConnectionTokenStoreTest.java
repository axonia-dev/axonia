package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.identity.domain.AgentConnectionToken;
import dev.axonia.identity.domain.AgentConnectionTokenKind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcAgentConnectionTokenStoreTest {

    private static final AgentConnectionTokenEntity ENTITY = AgentConnectionTokenEntity.builder().id(10L)
            .organizationId("organizationId-1").connectionId(12L).kind(AgentConnectionTokenKind.REFRESH)
            .tokenHash("tokenHash-4").expiresAt(Instant.parse("2026-06-06T10:00:00Z"))
            .consumedAt(Instant.parse("2026-07-07T10:00:00Z")).createdAt(Instant.parse("2026-08-08T10:00:00Z"))
            .updatedAt(Instant.parse("2026-09-09T10:00:00Z")).version(19L).build();

    private static final AgentConnectionToken DOMAIN = JdbcAgentConnectionTokenStore.toAgentConnectionToken(ENTITY);

    @Mock
    private AgentConnectionTokenRepository agentConnectionTokenRepository;

    @InjectMocks
    private JdbcAgentConnectionTokenStore store;

    @Test
    void mapsEveryColumnBothWays() {

        assertThat(DOMAIN).usingRecursiveComparison().isEqualTo(ENTITY);
        assertThat(JdbcAgentConnectionTokenStore.toEntity(DOMAIN)).usingRecursiveComparison().isEqualTo(ENTITY);
    }

    @Test
    void findByTokenHashMapsTheRowItFinds() {

        when(agentConnectionTokenRepository.findByTokenHash("tokenHash-0")).thenReturn(Optional.of(ENTITY));

        assertThat(store.findByTokenHash("tokenHash-0")).contains(DOMAIN);
    }

    @Test
    void saveWritesTheMappedRowAndAnswersWhatWasStored() {

        when(agentConnectionTokenRepository.save(any())).thenReturn(ENTITY);

        var actual = store.save(DOMAIN);

        var written = ArgumentCaptor.forClass(AgentConnectionTokenEntity.class);
        verify(agentConnectionTokenRepository).save(written.capture());
        assertThat(written.getValue()).usingRecursiveComparison().isEqualTo(ENTITY);
        assertThat(actual).isEqualTo(DOMAIN);
    }

    @Test
    void consumeAnswersTheRepositoryCount() {

        when(agentConnectionTokenRepository.consume(10L, Instant.parse("2026-02-02T10:00:00Z"))).thenReturn(3);

        assertThat(store.consume(10L, Instant.parse("2026-02-02T10:00:00Z"))).isEqualTo(3);
    }

    @Test
    void deleteAccessTokensDelegatesToTheRepository() {

        store.deleteAccessTokens(10L);

        verify(agentConnectionTokenRepository).deleteAccessTokens(10L);
    }

    @Test
    void deleteExpiredAnswersTheRepositoryCount() {

        when(agentConnectionTokenRepository.deleteExpired("organizationId-0", Instant.parse("2026-02-02T10:00:00Z")))
                .thenReturn(3);

        assertThat(store.deleteExpired("organizationId-0", Instant.parse("2026-02-02T10:00:00Z"))).isEqualTo(3);
    }
}
