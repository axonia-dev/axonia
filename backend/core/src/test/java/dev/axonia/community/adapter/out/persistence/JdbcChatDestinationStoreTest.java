package dev.axonia.community.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.community.domain.ChatDestinationRecord;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcChatDestinationStoreTest {

    private static final ChatDestinationRecord STORED = ChatDestinationRecord.builder().id(5L).organizationId("default")
            .communityId(1L).name("#guild").webhookUrl("https://hooks.slack.com/x").broken(true).consecutiveFailures(2)
            .lastPostedAt(Instant.parse("2026-06-01T00:00:00Z")).lastFailureAt(Instant.parse("2026-06-02T00:00:00Z"))
            .lastError("410 Gone").createdAt(Instant.parse("2026-05-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-06-02T00:00:00Z")).version(6L).build();

    @Mock
    private ChatDestinationRepository destinations;

    @InjectMocks
    private JdbcChatDestinationStore sut;

    @Test
    void listOf_maps_each_row_in_order() {

        // given
        when(destinations.findByOrganizationIdAndCommunityIdOrderByIdAsc("default", 1L))
                .thenReturn(List.of(JdbcChatDestinationStore.toEntity(STORED)));

        // then
        assertThat(sut.listOf("default", 1L)).containsExactly(STORED);
    }

    @Test
    void find_maps_every_column() {

        // given
        when(destinations.findByOrganizationIdAndId("default", 5L))
                .thenReturn(Optional.of(JdbcChatDestinationStore.toEntity(STORED)));

        // then
        assertThat(sut.find("default", 5L)).contains(STORED);
    }

    @Test
    void countOf_asks_the_repository() {

        // given
        when(destinations.countByOrganizationIdAndCommunityId("default", 1L)).thenReturn(3L);

        // then
        assertThat(sut.countOf("default", 1L)).isEqualTo(3L);
    }

    @Test
    void nameTaken_asks_the_repository() {

        // given
        when(destinations.existsByOrganizationIdAndCommunityIdAndName("default", 1L, "#guild")).thenReturn(true);

        // then
        assertThat(sut.nameTaken("default", 1L, "#guild")).isTrue();
    }

    @Test
    void idsOf_asks_the_repository() {

        // given
        when(destinations.findIdsByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(5L, 6L));

        // then
        assertThat(sut.idsOf("default", 1L)).containsExactly(5L, 6L);
    }

    @Test
    void add_writes_every_column_and_returns_what_was_stored() {

        // given
        when(destinations.save(any())).thenAnswer(call -> call.getArgument(0));

        // when
        var actual = sut.add(STORED);

        // then
        var captor = ArgumentCaptor.forClass(ChatDestinationEntity.class);
        verify(destinations).save(captor.capture());
        assertThat(JdbcChatDestinationStore.toRecord(captor.getValue())).isEqualTo(STORED);
        assertThat(actual).isEqualTo(STORED);
    }

    /**
     * The race the service's name check cannot reach: two concurrent creates for the same name both pass its read, and
     * the loser hits the database's own {@code community_chat_destination_named_once} constraint instead. This is that
     * loser's path, the save failing the way Postgres actually reports a unique violation.
     */
    @Test
    void add_refuses_a_name_the_race_reached_the_database_with() {

        // given
        when(destinations.save(any())).thenThrow(new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"community_chat_destination_named_once\""));

        // then
        assertThatThrownBy(() -> sut.add(STORED)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("already has a destination");
    }

    /**
     * The narrowing {@link JdbcChatDestinationStore#add}'s own javadoc promises: a
     * {@link DataIntegrityViolationException} from some other constraint - or a genuine outage - is not the name race
     * and must not be misreported as one.
     */
    @Test
    void add_does_not_misreport_an_unrelated_failure_as_a_name_collision() {

        // given
        when(destinations.save(any())).thenThrow(new DataIntegrityViolationException("connection to server was lost"));

        // then
        assertThatThrownBy(() -> sut.add(STORED)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("connection to server");
    }

    @Test
    void update_writes_every_column_and_returns_what_was_stored() {

        // given
        when(destinations.save(any())).thenAnswer(call -> call.getArgument(0));

        // when
        var actual = sut.update(STORED);

        // then
        var captor = ArgumentCaptor.forClass(ChatDestinationEntity.class);
        verify(destinations).save(captor.capture());
        assertThat(JdbcChatDestinationStore.toRecord(captor.getValue())).isEqualTo(STORED);
        assertThat(actual).isEqualTo(STORED);
    }

    @Test
    void remove_carries_id_and_version() {

        // when
        sut.remove(STORED);

        // then
        var captor = ArgumentCaptor.forClass(ChatDestinationEntity.class);
        verify(destinations).delete(captor.capture());
        assertThat(JdbcChatDestinationStore.toRecord(captor.getValue())).isEqualTo(STORED);
    }
}
