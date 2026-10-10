package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcCalendarSubscriptionStoreTest {

    private static final Instant NOW = Instant.parse("2026-06-03T10:00:00Z");

    private static final CalendarSubscriptionEntity STORED = CalendarSubscriptionEntity.builder().id(2L)
            .organizationId("default").communityId(1L).userId("bob").tokenHash("hash").createdAt(NOW.minusSeconds(60))
            .lastFetchedAt(NOW).lastFetchedAgent("Calendar/1.0").version(3L).build();

    @Mock
    private CalendarSubscriptionRepository calendarSubscriptionRepository;

    @InjectMocks
    private JdbcCalendarSubscriptionStore sut;

    @Test
    void the_mapping_round_trips_every_column() {

        // when
        var record = JdbcCalendarSubscriptionStore.toRecord(STORED);

        // then
        assertThat(record).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(JdbcCalendarSubscriptionStore.toEntity(record)).usingRecursiveComparison().isEqualTo(STORED);
    }

    @Test
    void the_reads_map_what_the_repository_found() {

        // given
        when(calendarSubscriptionRepository.findByTokenHash("hash")).thenReturn(Optional.of(STORED));
        when(calendarSubscriptionRepository.findByOrganizationIdAndCommunityIdAndUserId("default", 1L, "bob"))
                .thenReturn(Optional.empty());

        // then
        assertThat(sut.findByTokenHash("hash")).get().usingRecursiveComparison().isEqualTo(STORED);
        assertThat(sut.findByOrganizationIdAndCommunityIdAndUserId("default", 1L, "bob")).isEmpty();
    }

    @Test
    void stampFetch_passes_straight_through() {

        // when
        sut.stampFetch(2L, NOW, "Calendar/1.0");

        // then
        verify(calendarSubscriptionRepository).stampFetch(2L, NOW, "Calendar/1.0");
    }

    @Test
    void save_writes_the_record_as_an_entity() {

        // given
        when(calendarSubscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var saved = sut.save(JdbcCalendarSubscriptionStore.toRecord(STORED));

        // then
        var captor = ArgumentCaptor.forClass(CalendarSubscriptionEntity.class);
        verify(calendarSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(saved).usingRecursiveComparison().isEqualTo(STORED);
    }
}
