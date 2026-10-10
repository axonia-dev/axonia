package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
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

import dev.axonia.events.api.TopicKind;
import dev.axonia.events.api.TopicStatus;
import dev.axonia.events.application.TopicFilter;
import dev.axonia.events.domain.TopicCounts;
import dev.axonia.events.domain.TopicRecord;
import dev.axonia.events.domain.TopicSchedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcTopicStoreTest {

    private static final TopicEntity STORED = TopicEntity.builder().id(9L).organizationId("default").communityId(1L)
            .kind(TopicKind.OFFER).title("Records").description("Java records").proposerUserId("alice")
            .status(TopicStatus.SCHEDULED).tags(Set.of(TopicTagRef.builder().tag("java").build()))
            .createdAt(Instant.parse("2026-06-01T00:00:00Z")).updatedAt(Instant.parse("2026-06-02T00:00:00Z"))
            .version(3L).build();

    @Mock
    private TopicRepository topicRepository;

    @InjectMocks
    private JdbcTopicStore sut;

    @Test
    void the_mapping_round_trips_every_column() {

        // when
        var record = JdbcTopicStore.toRecord(STORED);

        // then
        assertThat(record).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(JdbcTopicStore.toEntity(record)).usingRecursiveComparison().isEqualTo(STORED);
    }

    @Test
    void the_reads_map_what_the_repository_found() {

        // given
        var pageable = PageRequest.of(0, 10);
        var filter = new TopicFilter(TopicKind.OFFER, TopicStatus.SCHEDULED);
        var schedule = new TopicSchedule(9L, 7L, 11L, 21L, Instant.parse("2026-06-03T10:00:00Z"), "Dojo");
        var counts = new TopicCounts(1, 2, 3);
        when(topicRepository.findByOrganizationIdAndId("default", 9L)).thenReturn(Optional.of(STORED));
        when(topicRepository.findFiltered("default", 1L, filter, pageable))
                .thenReturn(new PageImpl<>(List.of(STORED), pageable, 1));
        when(topicRepository.findPast("default", 1L, "rec", pageable))
                .thenReturn(new PageImpl<>(List.of(STORED), pageable, 1));
        when(topicRepository.findSchedules("default", List.of(9L))).thenReturn(List.of(schedule));
        when(topicRepository.countByLifecycle("default", 1L)).thenReturn(counts);

        // then
        assertThat(sut.findByOrganizationIdAndId("default", 9L)).get().usingRecursiveComparison().isEqualTo(STORED);
        assertThat(sut.findFiltered("default", 1L, filter, pageable).getContent()).extracting(TopicRecord::getId)
                .containsExactly(9L);
        assertThat(sut.findPast("default", 1L, "rec", pageable).getContent()).extracting(TopicRecord::getId)
                .containsExactly(9L);
        assertThat(sut.findSchedules("default", List.of(9L))).containsExactly(schedule);
        assertThat(sut.countByLifecycle("default", 1L)).isEqualTo(counts);
    }

    @Test
    void save_and_delete_write_the_record_as_an_entity() {

        // given
        when(topicRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var saved = sut.save(JdbcTopicStore.toRecord(STORED));
        sut.delete(JdbcTopicStore.toRecord(STORED));

        // then
        var captor = ArgumentCaptor.forClass(TopicEntity.class);
        verify(topicRepository).save(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(saved).usingRecursiveComparison().isEqualTo(STORED);
        verify(topicRepository).delete(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(STORED);
    }
}
