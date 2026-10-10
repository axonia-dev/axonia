package dev.axonia.community.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.domain.CommunityKindRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcCommunityKindStoreTest {

    private static final CommunityKindRecord STORED = CommunityKindRecord.builder().id(3L).organizationId("default")
            .label("Guild").createdAt(Instant.parse("2026-06-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-06-02T00:00:00Z")).version(2L).build();

    @Mock
    private CommunityKindRepository communityKindRepository;

    @InjectMocks
    private JdbcCommunityKindStore sut;

    @Test
    void listByLabel_maps_each_row_in_order() {

        // given
        when(communityKindRepository.findByOrganizationIdOrderByLabelAsc("default"))
                .thenReturn(List.of(JdbcCommunityKindStore.toEntity(STORED)));

        // then
        assertThat(sut.listByLabel("default")).containsExactly(STORED);
    }

    @Test
    void find_maps_every_column() {

        // given
        when(communityKindRepository.findByOrganizationIdAndId("default", 3L))
                .thenReturn(Optional.of(JdbcCommunityKindStore.toEntity(STORED)));

        // then
        assertThat(sut.find("default", 3L)).contains(STORED);
    }

    @Test
    void labelTaken_asks_the_repository() {

        // given
        when(communityKindRepository.existsByOrganizationIdAndLabel("default", "Guild")).thenReturn(true);

        // then
        assertThat(sut.labelTaken("default", "Guild")).isTrue();
    }

    @Test
    void save_writes_every_column_and_returns_what_was_stored() {

        // given
        when(communityKindRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        // when
        var actual = sut.save(STORED);

        // then
        var captor = ArgumentCaptor.forClass(CommunityKindEntity.class);
        verify(communityKindRepository).save(captor.capture());
        assertThat(JdbcCommunityKindStore.toRecord(captor.getValue())).isEqualTo(STORED);
        assertThat(actual).isEqualTo(STORED);
    }

    @Test
    void delete_carries_id_and_version() {

        // when
        sut.delete(STORED);

        // then
        var captor = ArgumentCaptor.forClass(CommunityKindEntity.class);
        verify(communityKindRepository).delete(captor.capture());
        assertThat(JdbcCommunityKindStore.toRecord(captor.getValue())).isEqualTo(STORED);
    }
}
