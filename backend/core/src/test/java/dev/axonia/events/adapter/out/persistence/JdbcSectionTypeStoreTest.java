package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.events.domain.SectionTypeRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcSectionTypeStoreTest {

    private static final SectionTypeEntity STORED = SectionTypeEntity.builder().id(5L).organizationId("default")
            .label("Talks").expectsPresenter(true).icon("mic").accentColor("#ff0000")
            .createdAt(Instant.parse("2026-06-01T00:00:00Z")).updatedAt(Instant.parse("2026-06-02T00:00:00Z"))
            .version(1L).build();

    @Mock
    private SectionTypeRepository sectionTypeRepository;

    @InjectMocks
    private JdbcSectionTypeStore sut;

    @Test
    void the_mapping_round_trips_every_column() {

        // when
        var record = JdbcSectionTypeStore.toRecord(STORED);

        // then
        assertThat(record).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(JdbcSectionTypeStore.toEntity(record)).usingRecursiveComparison().isEqualTo(STORED);
    }

    @Test
    void the_reads_map_what_the_repository_found() {

        // given
        when(sectionTypeRepository.findByOrganizationIdOrderByLabelAsc("default")).thenReturn(List.of(STORED));
        when(sectionTypeRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(STORED));
        when(sectionTypeRepository.findByOrganizationIdAndIdIn("default", List.of(5L))).thenReturn(List.of(STORED));
        when(sectionTypeRepository.existsByOrganizationIdAndLabel("default", "Talks")).thenReturn(true);
        when(sectionTypeRepository.countStructureRefs(5L)).thenReturn(2L);
        when(sectionTypeRepository.countSectionRefs(5L)).thenReturn(3L);

        // then
        assertThat(sut.findByOrganizationIdOrderByLabelAsc("default")).extracting(SectionTypeRecord::getId)
                .containsExactly(5L);
        assertThat(sut.findByOrganizationIdAndId("default", 5L)).get().usingRecursiveComparison().isEqualTo(STORED);
        assertThat(sut.findByOrganizationIdAndIdIn("default", List.of(5L))).extracting(SectionTypeRecord::getId)
                .containsExactly(5L);
        assertThat(sut.existsByOrganizationIdAndLabel("default", "Talks")).isTrue();
        assertThat(sut.countStructureRefs(5L)).isEqualTo(2L);
        assertThat(sut.countSectionRefs(5L)).isEqualTo(3L);
    }

    @Test
    void the_reassignments_pass_straight_through() {

        // when
        sut.reassignStructureRefs(5L, 6L);
        sut.reassignSectionRefs(5L, 6L);

        // then
        verify(sectionTypeRepository).reassignStructureRefs(5L, 6L);
        verify(sectionTypeRepository).reassignSectionRefs(5L, 6L);
    }

    @Test
    void save_and_delete_write_the_record_as_an_entity() {

        // given
        when(sectionTypeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var saved = sut.save(JdbcSectionTypeStore.toRecord(STORED));
        sut.delete(JdbcSectionTypeStore.toRecord(STORED));

        // then
        var captor = ArgumentCaptor.forClass(SectionTypeEntity.class);
        verify(sectionTypeRepository).save(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(saved).usingRecursiveComparison().isEqualTo(STORED);
        verify(sectionTypeRepository).delete(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(STORED);
    }
}
