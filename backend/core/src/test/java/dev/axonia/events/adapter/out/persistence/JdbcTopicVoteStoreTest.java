package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.events.domain.TopicVoteRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcTopicVoteStoreTest {

    private static final TopicVoteEntity STORED = TopicVoteEntity.builder().id(4L).organizationId("default").topicId(9L)
            .userId("bob").createdAt(Instant.parse("2026-06-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-06-02T00:00:00Z")).version(1L).build();

    @Mock
    private TopicVoteRepository topicVoteRepository;

    @InjectMocks
    private JdbcTopicVoteStore sut;

    @Test
    void the_mapping_round_trips_every_column() {

        // when
        var record = JdbcTopicVoteStore.toRecord(STORED);

        // then
        assertThat(record).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(JdbcTopicVoteStore.toEntity(record)).usingRecursiveComparison().isEqualTo(STORED);
    }

    @Test
    void the_reads_map_what_the_repository_found() {

        // given
        when(topicVoteRepository.findByOrganizationIdAndTopicIdAndUserId("default", 9L, "bob"))
                .thenReturn(Optional.of(STORED));
        when(topicVoteRepository.countByOrganizationIdAndTopicId("default", 9L)).thenReturn(2L);
        when(topicVoteRepository.existsByOrganizationIdAndTopicIdAndUserId("default", 9L, "bob")).thenReturn(true);
        when(topicVoteRepository.findByOrganizationIdAndTopicIdIn("default", List.of(9L))).thenReturn(List.of(STORED));

        // then
        assertThat(sut.findByOrganizationIdAndTopicIdAndUserId("default", 9L, "bob")).get().usingRecursiveComparison()
                .isEqualTo(STORED);
        assertThat(sut.countByOrganizationIdAndTopicId("default", 9L)).isEqualTo(2L);
        assertThat(sut.existsByOrganizationIdAndTopicIdAndUserId("default", 9L, "bob")).isTrue();
        assertThat(sut.findByOrganizationIdAndTopicIdIn("default", List.of(9L))).extracting(TopicVoteRecord::getId)
                .containsExactly(4L);
    }

    @Test
    void save_and_delete_write_the_record_as_an_entity() {

        // given
        when(topicVoteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var saved = sut.save(JdbcTopicVoteStore.toRecord(STORED));
        sut.delete(JdbcTopicVoteStore.toRecord(STORED));

        // then
        var captor = ArgumentCaptor.forClass(TopicVoteEntity.class);
        verify(topicVoteRepository).save(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(saved).usingRecursiveComparison().isEqualTo(STORED);
        verify(topicVoteRepository).delete(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(STORED);
    }
}
