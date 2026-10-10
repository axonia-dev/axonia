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

import dev.axonia.identity.domain.AgentConnection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcAgentConnectionStoreTest {

    private static final AgentConnectionEntity ENTITY = AgentConnectionEntity.builder().id(10L)
            .organizationId("organizationId-1").userId("userId-2").clientId("clientId-3").clientName("clientName-4")
            .clientHost("clientHost-5").scopes("scopes-6").resource("resource-7").redirectUri("redirectUri-8")
            .codeChallenge("codeChallenge-9").activatedAt(Instant.parse("2026-02-02T10:00:00Z"))
            .lastUsedAt(Instant.parse("2026-03-03T10:00:00Z")).endsAt(Instant.parse("2026-04-04T10:00:00Z"))
            .revokedAt(Instant.parse("2026-05-05T10:00:00Z")).createdAt(Instant.parse("2026-06-06T10:00:00Z"))
            .updatedAt(Instant.parse("2026-07-07T10:00:00Z")).version(26L).build();

    private static final AgentConnection DOMAIN = JdbcAgentConnectionStore.toAgentConnection(ENTITY);

    @Mock
    private AgentConnectionRepository agentConnectionRepository;

    @InjectMocks
    private JdbcAgentConnectionStore store;

    @Test
    void mapsEveryColumnBothWays() {

        assertThat(DOMAIN).usingRecursiveComparison().isEqualTo(ENTITY);
        assertThat(JdbcAgentConnectionStore.toEntity(DOMAIN)).usingRecursiveComparison().isEqualTo(ENTITY);
    }

    @Test
    void findByIdMapsTheRowItFinds() {

        when(agentConnectionRepository.findById(10L)).thenReturn(Optional.of(ENTITY));

        assertThat(store.findById(10L)).contains(DOMAIN);
    }

    @Test
    void findByOrganizationIdAndUserIdAndActivatedAtIsNotNullOrderByActivatedAtDescMapsEveryRowItFinds() {

        when(agentConnectionRepository.findByOrganizationIdAndUserIdAndActivatedAtIsNotNullOrderByActivatedAtDesc(
                "organizationId-0", "userId-1")).thenReturn(List.of(ENTITY));

        assertThat(store.findByOrganizationIdAndUserIdAndActivatedAtIsNotNullOrderByActivatedAtDesc("organizationId-0",
                "userId-1")).containsExactly(DOMAIN);
    }

    @Test
    void findByOrganizationIdAndUserIdAndIdMapsTheRowItFinds() {

        when(agentConnectionRepository.findByOrganizationIdAndUserIdAndId("organizationId-0", "userId-1", 12L))
                .thenReturn(Optional.of(ENTITY));

        assertThat(store.findByOrganizationIdAndUserIdAndId("organizationId-0", "userId-1", 12L)).contains(DOMAIN);
    }

    @Test
    void findByOrganizationIdAndUserIdAndClientIdAndActivatedAtIsNotNullAndRevokedAtIsNullMapsEveryRowItFinds() {

        when(agentConnectionRepository
                .findByOrganizationIdAndUserIdAndClientIdAndActivatedAtIsNotNullAndRevokedAtIsNull("organizationId-0",
                        "userId-1", "clientId-2"))
                .thenReturn(List.of(ENTITY));

        assertThat(store.findByOrganizationIdAndUserIdAndClientIdAndActivatedAtIsNotNullAndRevokedAtIsNull(
                "organizationId-0", "userId-1", "clientId-2")).containsExactly(DOMAIN);
    }

    @Test
    void saveWritesTheMappedRowAndAnswersWhatWasStored() {

        when(agentConnectionRepository.save(any())).thenReturn(ENTITY);

        var actual = store.save(DOMAIN);

        var written = ArgumentCaptor.forClass(AgentConnectionEntity.class);
        verify(agentConnectionRepository).save(written.capture());
        assertThat(written.getValue()).usingRecursiveComparison().isEqualTo(ENTITY);
        assertThat(actual).isEqualTo(DOMAIN);
    }

    @Test
    void touchLastUsedDelegatesToTheRepository() {

        store.touchLastUsed("organizationId-0", 11L, Instant.parse("2026-03-03T10:00:00Z"),
                Instant.parse("2026-04-04T10:00:00Z"));

        verify(agentConnectionRepository).touchLastUsed("organizationId-0", 11L, Instant.parse("2026-03-03T10:00:00Z"),
                Instant.parse("2026-04-04T10:00:00Z"));
    }

    @Test
    void deleteRetiredAnswersTheRepositoryCount() {

        when(agentConnectionRepository.deleteRetired("organizationId-0", Instant.parse("2026-02-02T10:00:00Z"),
                Instant.parse("2026-03-03T10:00:00Z"))).thenReturn(3);

        assertThat(store.deleteRetired("organizationId-0", Instant.parse("2026-02-02T10:00:00Z"),
                Instant.parse("2026-03-03T10:00:00Z"))).isEqualTo(3);
    }
}
