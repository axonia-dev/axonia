package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import dev.axonia.events.api.ActionItemOutcome;
import dev.axonia.events.application.ActionItemFilter;
import dev.axonia.events.domain.ActionItemRecord;
import dev.axonia.events.domain.LateBindingPin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcActionItemStoreTest {

    private static final Instant NOW = Instant.parse("2026-06-03T10:00:00Z");

    private static final ActionItemEntity STORED = ActionItemEntity.builder().id(5L).organizationId("default")
            .communityId(1L).title("Write it up").description("The notes").raisedByUserId("alice").assigneeUserId("bob")
            .dueDate(LocalDate.of(2026, 6, 10)).originSessionId(7L).checkpointSessionId(8L)
            .outcome(ActionItemOutcome.OPEN).tags(Set.of(ActionItemTagRef.builder().tag("docs").build()))
            .createdAt(NOW.minusSeconds(60)).updatedAt(NOW).version(2L).build();

    @Mock
    private ActionItemRepository actionItemRepository;

    @InjectMocks
    private JdbcActionItemStore sut;

    @Test
    void the_mapping_round_trips_every_column() {

        // when
        var record = JdbcActionItemStore.toRecord(STORED);

        // then
        assertThat(record).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(JdbcActionItemStore.toEntity(record)).usingRecursiveComparison().isEqualTo(STORED);
    }

    @Test
    void the_reads_map_what_the_repository_found() {

        // given
        var pageable = PageRequest.of(0, 10);
        var filter = new ActionItemFilter(ActionItemOutcome.OPEN, 7L, null);
        when(actionItemRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(STORED));
        when(actionItemRepository.findByOrganizationIdAndIdIn("default", List.of(5L))).thenReturn(List.of(STORED));
        when(actionItemRepository.findOpenByCheckpointSession("default", 1L, 8L)).thenReturn(List.of(STORED));
        when(actionItemRepository.findByOriginSessionId("default", 1L, 7L)).thenReturn(List.of(STORED));
        when(actionItemRepository.findFiltered("default", 1L, filter, pageable))
                .thenReturn(new PageImpl<>(List.of(STORED), pageable, 1));

        // then
        assertThat(sut.findByOrganizationIdAndId("default", 5L)).get().usingRecursiveComparison().isEqualTo(STORED);
        assertThat(sut.findByOrganizationIdAndIdIn("default", List.of(5L))).extracting(ActionItemRecord::getId)
                .containsExactly(5L);
        assertThat(sut.findOpenByCheckpointSession("default", 1L, 8L)).extracting(ActionItemRecord::getId)
                .containsExactly(5L);
        assertThat(sut.findByOriginSessionId("default", 1L, 7L)).extracting(ActionItemRecord::getId)
                .containsExactly(5L);
        assertThat(sut.findFiltered("default", 1L, filter, pageable).getContent()).extracting(ActionItemRecord::getId)
                .containsExactly(5L);
    }

    @Test
    void the_bulk_pin_writes_pass_straight_through() {

        // given
        var pin = new LateBindingPin("default", 1L, 7L);

        // when
        sut.pinLateBound(pin, NOW, NOW.plusSeconds(1));
        sut.clearPinsFor("default", 1L, 7L, NOW);

        // then
        verify(actionItemRepository).pinLateBound(pin, NOW, NOW.plusSeconds(1));
        verify(actionItemRepository).clearPinsFor("default", 1L, 7L, NOW);
    }

    @Test
    void save_and_delete_write_the_record_as_an_entity() {

        // given
        when(actionItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var saved = sut.save(JdbcActionItemStore.toRecord(STORED));
        sut.delete(JdbcActionItemStore.toRecord(STORED));

        // then
        var captor = ArgumentCaptor.forClass(ActionItemEntity.class);
        verify(actionItemRepository).save(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(saved).usingRecursiveComparison().isEqualTo(STORED);
        verify(actionItemRepository).delete(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(STORED);
    }
}
