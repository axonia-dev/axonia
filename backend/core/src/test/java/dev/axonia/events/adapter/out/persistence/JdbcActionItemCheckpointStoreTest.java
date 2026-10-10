package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.events.domain.ActionItemCheckpointRecord;
import dev.axonia.events.domain.CheckpointOutcome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcActionItemCheckpointStoreTest {

    private static final ActionItemCheckpointEntity STORED = ActionItemCheckpointEntity.builder().id(3L)
            .organizationId("default").actionItemId(5L).sessionId(7L).outcome(CheckpointOutcome.CARRIED)
            .decidedByUserId("alice").decidedAt(Instant.parse("2026-06-03T10:00:00Z")).build();

    @Mock
    private ActionItemCheckpointRepository actionItemCheckpointRepository;

    @InjectMocks
    private JdbcActionItemCheckpointStore sut;

    @Test
    void the_mapping_round_trips_every_column() {

        // when
        var record = JdbcActionItemCheckpointStore.toRecord(STORED);

        // then
        assertThat(record).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(JdbcActionItemCheckpointStore.toEntity(record)).usingRecursiveComparison().isEqualTo(STORED);
    }

    @Test
    void the_reads_map_what_the_repository_found() {

        // given
        when(actionItemCheckpointRepository.findByOrganizationIdAndCommunityIdAndSessionId("default", 1L, 7L))
                .thenReturn(List.of(STORED));
        when(actionItemCheckpointRepository.countByOrganizationIdAndActionItemIdAndOutcome("default", 5L,
                CheckpointOutcome.CARRIED)).thenReturn(2L);
        when(actionItemCheckpointRepository.findByOrganizationIdAndActionItemIdInAndOutcome("default", List.of(5L),
                CheckpointOutcome.CARRIED)).thenReturn(List.of(STORED));

        // then
        assertThat(sut.findByOrganizationIdAndCommunityIdAndSessionId("default", 1L, 7L))
                .extracting(ActionItemCheckpointRecord::getId).containsExactly(3L);
        assertThat(sut.countByOrganizationIdAndActionItemIdAndOutcome("default", 5L, CheckpointOutcome.CARRIED))
                .isEqualTo(2L);
        assertThat(
                sut.findByOrganizationIdAndActionItemIdInAndOutcome("default", List.of(5L), CheckpointOutcome.CARRIED))
                .extracting(ActionItemCheckpointRecord::getId).containsExactly(3L);
    }

    @Test
    void save_writes_the_record_as_an_entity() {

        // given
        when(actionItemCheckpointRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var saved = sut.save(JdbcActionItemCheckpointStore.toRecord(STORED));

        // then
        var captor = ArgumentCaptor.forClass(ActionItemCheckpointEntity.class);
        verify(actionItemCheckpointRepository).save(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(saved).usingRecursiveComparison().isEqualTo(STORED);
    }
}
