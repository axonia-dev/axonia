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

import dev.axonia.events.application.PastSessionsQuery;
import dev.axonia.events.domain.SessionRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcSessionStoreTest {

    private static final Instant NOW = Instant.parse("2026-06-03T10:00:00Z");

    private static final SessionEntity STORED = SessionEntity.builder().id(7L).organizationId("default").seriesId(3L)
            .communityId(1L).status("HELD").scheduledAt(NOW).hasTime(true).timeZone("Europe/Zurich")
            .occurrenceDate(LocalDate.of(2026, 6, 3)).title("Dojo").durationMinutes(90).facilitatorUserId("alice")
            .notes("notes").recordingUrl("https://rec").location("Room 1").joinUrl("https://join")
            .joiningDetails("code 42").closedAt(NOW.plusSeconds(60)).wrapUpReleaseAt(NOW.plusSeconds(120))
            .upcomingReleaseAt(NOW.minusSeconds(60))
            .sections(List.of(SectionEntity.builder().id(11L).sectionTypeId(5L).title("Talks").durationMinutes(45)
                    .notes("section notes")
                    .slots(List.of(SlotEntity.builder().id(21L).topicId(31L).title("Slot").notes("slot notes")
                            .durationMinutes(15).presenters(Set.of(SlotPresenterRef.builder().userId("bob").build()))
                            .tags(Set.of(SlotTagRef.builder().tag("java").build())).build()))
                    .build()))
            .createdAt(NOW.minusSeconds(3600)).updatedAt(NOW).version(4L).build();

    @Mock
    private SessionRepository sessionRepository;

    @InjectMocks
    private JdbcSessionStore sut;

    @Test
    void toRecord_maps_every_column_down_to_presenters_and_tags() {

        // when
        var actual = JdbcSessionStore.toRecord(STORED);

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(STORED);
    }

    @Test
    void toEntity_round_trips_every_column() {

        // when
        var actual = JdbcSessionStore.toEntity(JdbcSessionStore.toRecord(STORED));

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(STORED);
    }

    @Test
    void a_missing_collection_stays_missing_both_ways() {

        // given
        var bare = SessionEntity.builder().id(1L)
                .sections(List
                        .of(SectionEntity.builder().id(2L).slots(List.of(SlotEntity.builder().id(3L).build())).build()))
                .build();
        var noSections = SessionEntity.builder().id(4L).build();
        var noSlots = SessionEntity.builder().id(5L).sections(List.of(SectionEntity.builder().id(6L).build())).build();

        // when
        var slot = JdbcSessionStore.toRecord(bare).getSections().getFirst().getSlots().getFirst();

        // then
        assertThat(slot.getPresenters()).isNull();
        assertThat(slot.getTags()).isNull();
        assertThat(JdbcSessionStore.toRecord(noSections).getSections()).isNull();
        assertThat(JdbcSessionStore.toEntity(JdbcSessionStore.toRecord(noSections)).getSections()).isNull();
        assertThat(JdbcSessionStore.toRecord(noSlots).getSections().getFirst().getSlots()).isNull();
        assertThat(JdbcSessionStore.toEntity(JdbcSessionStore.toRecord(bare)).getSections().getFirst().getSlots()
                .getFirst().getPresenters()).isNull();
    }

    @Test
    void the_single_session_reads_map_what_the_repository_found() {

        // given
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(STORED));
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 3L,
                LocalDate.of(2026, 6, 3))).thenReturn(Optional.of(STORED));
        when(sessionRepository.findPendingForSeriesExcluding("default", 3L, 9L, NOW)).thenReturn(Optional.of(STORED));
        when(sessionRepository.findSoonestUpcoming("default", 1L, NOW)).thenReturn(Optional.empty());

        // then
        assertThat(sut.findByOrganizationIdAndId("default", 7L)).get().usingRecursiveComparison().isEqualTo(STORED);
        assertThat(sut.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 3L, LocalDate.of(2026, 6, 3))).get()
                .usingRecursiveComparison().isEqualTo(STORED);
        assertThat(sut.findPendingForSeriesExcluding("default", 3L, 9L, NOW)).get().usingRecursiveComparison()
                .isEqualTo(STORED);
        assertThat(sut.findSoonestUpcoming("default", 1L, NOW)).isEmpty();
    }

    @Test
    void the_batched_read_keeps_the_repository_order() {

        // given
        var other = STORED.toBuilder().id(8L).build();
        when(sessionRepository.findByOrganizationIdAndCommunityIdAndIdIn("default", 1L, List.of(8L, 7L)))
                .thenReturn(List.of(other, STORED));

        // when
        var actual = sut.findByOrganizationIdAndCommunityIdAndIdIn("default", 1L, List.of(8L, 7L));

        // then
        assertThat(actual).extracting(SessionRecord::getId).containsExactly(8L, 7L);
    }

    @Test
    void the_counts_and_id_reads_pass_straight_through() {

        // given
        var query = PastSessionsQuery.builder().organizationId("default").communityId(1L).now(NOW).build();
        when(sessionRepository.countUpcoming("default", 1L, NOW)).thenReturn(3L);
        when(sessionRepository.countPast(query)).thenReturn(5L);
        when(sessionRepository.findActiveMonths("default", 1L)).thenReturn(List.of("2026-06"));
        when(sessionRepository.findUpcomingNoticeSessionIds("default", NOW, Instant.EPOCH)).thenReturn(List.of(1L));
        when(sessionRepository.findWrapUpSessionIds("default", NOW, Instant.EPOCH)).thenReturn(List.of(2L));
        when(sessionRepository.findOverdueUnclosedSessionIds("default", 7, Instant.EPOCH, NOW)).thenReturn(List.of(3L));

        // then
        assertThat(sut.countUpcoming("default", 1L, NOW)).isEqualTo(3L);
        assertThat(sut.countPast(query)).isEqualTo(5L);
        assertThat(sut.findActiveMonths("default", 1L)).containsExactly("2026-06");
        assertThat(sut.findUpcomingNoticeSessionIds("default", NOW, Instant.EPOCH)).containsExactly(1L);
        assertThat(sut.findWrapUpSessionIds("default", NOW, Instant.EPOCH)).containsExactly(2L);
        assertThat(sut.findOverdueUnclosedSessionIds("default", 7, Instant.EPOCH, NOW)).containsExactly(3L);
    }

    @Test
    void save_writes_the_record_as_an_entity_and_returns_what_was_saved() {

        // given
        var saved = STORED.toBuilder().version(5L).build();
        when(sessionRepository.save(any())).thenReturn(saved);

        // when
        var actual = sut.save(JdbcSessionStore.toRecord(STORED));

        // then
        var captor = ArgumentCaptor.forClass(SessionEntity.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(actual.getVersion()).isEqualTo(5L);
    }
}
