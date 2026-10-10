package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import dev.axonia.events.domain.CadenceUnit;
import dev.axonia.events.domain.SeriesRecord;
import dev.axonia.events.domain.SeriesStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcSeriesStoreTest {

    private static final SeriesEntity STORED = SeriesEntity.builder().id(3L).organizationId("default").communityId(1L)
            .title("Dojo").cadenceCount(2).cadenceUnit(CadenceUnit.WEEK).startsOn(LocalDate.of(2026, 6, 3))
            .preferredTime(LocalTime.of(18, 0)).timeZone("Europe/Zurich").durationMinutes(90)
            .status(SeriesStatus.ACTIVE).location("Room 1").joinUrl("https://join").joiningDetails("code 42")
            .defaultStructure(
                    List.of(SectionTypeRef.builder().sectionTypeId(5L).title("Talks").durationMinutes(45).build()))
            .createdAt(Instant.parse("2026-06-01T00:00:00Z")).updatedAt(Instant.parse("2026-06-02T00:00:00Z"))
            .version(2L).build();

    @Mock
    private SeriesRepository seriesRepository;

    @InjectMocks
    private JdbcSeriesStore sut;

    @Test
    void the_mapping_round_trips_every_column() {

        // when
        var record = JdbcSeriesStore.toRecord(STORED);

        // then
        assertThat(record).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(JdbcSeriesStore.toEntity(record)).usingRecursiveComparison().isEqualTo(STORED);
    }

    @Test
    void the_reads_map_what_the_repository_found() {

        // given
        var pageable = PageRequest.of(0, 10);
        when(seriesRepository.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(STORED));
        when(seriesRepository.findByOrganizationIdAndIdIn("default", List.of(3L))).thenReturn(List.of(STORED));
        when(seriesRepository.findByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(STORED));
        when(seriesRepository.findByOrganizationIdAndCommunityId("default", 1L, pageable))
                .thenReturn(new PageImpl<>(List.of(STORED), pageable, 1));

        // then
        assertThat(sut.findByOrganizationIdAndId("default", 3L)).get().usingRecursiveComparison().isEqualTo(STORED);
        assertThat(sut.findByOrganizationIdAndIdIn("default", List.of(3L))).extracting(SeriesRecord::getId)
                .containsExactly(3L);
        assertThat(sut.findByOrganizationIdAndCommunityId("default", 1L)).extracting(SeriesRecord::getId)
                .containsExactly(3L);
        assertThat(sut.findByOrganizationIdAndCommunityId("default", 1L, pageable).getContent())
                .extracting(SeriesRecord::getId).containsExactly(3L);
    }

    @Test
    void save_writes_the_record_as_an_entity() {

        // given
        when(seriesRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var actual = sut.save(JdbcSeriesStore.toRecord(STORED));

        // then
        var captor = ArgumentCaptor.forClass(SeriesEntity.class);
        verify(seriesRepository).save(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(actual).usingRecursiveComparison().isEqualTo(STORED);
    }
}
