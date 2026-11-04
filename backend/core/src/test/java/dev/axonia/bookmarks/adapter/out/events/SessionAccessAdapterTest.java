package dev.axonia.bookmarks.adapter.out.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.events.api.Section;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.api.Sessions;
import dev.axonia.events.api.Slot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class SessionAccessAdapterTest {

    @Mock
    private Sessions sessionService;

    @InjectMocks
    private SessionAccessAdapter sut;

    @Test
    void get_namesTheSavedSessionAsItsPageDoesAndSumsSlotsAcrossSections() {

        // given a session named on its own, whose slots carry titles of their own
        var scheduledAt = Instant.parse("2026-04-15T18:00:00Z");
        var occurrenceDate = LocalDate.of(2026, 4, 15);
        var firstSection = Section.builder()
                .slots(List.of(Slot.builder().title("P1s").build(), Slot.builder().title("Live Coding").build()))
                .build();
        var secondSection = Section.builder().slots(List.of(Slot.builder().title(null).build())).build();
        var session = Session.builder().id(100L).communityId(7L).status(SessionStatus.SCHEDULED)
                .title("S26E10 - DevTool").displayTitle("S26E10 - DevTool").scheduledAt(scheduledAt)
                .occurrenceDate(occurrenceDate).facilitatorUserId("alice").recordingUrl("https://example.test/rec.mp4")
                .sections(List.of(firstSection, secondSection)).build();
        when(sessionService.get(100L)).thenReturn(session);

        // when
        var actual = sut.get(100L);

        // then the row carries the page's name, and slotCount sums slots across both sections
        assertThat(actual.id()).isEqualTo(100L);
        assertThat(actual.communityId()).isEqualTo(7L);
        assertThat(actual.title()).isEqualTo("S26E10 - DevTool");
        assertThat(actual.status()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(actual.scheduledAt()).isEqualTo(scheduledAt);
        assertThat(actual.occurrenceDate()).isEqualTo(occurrenceDate);
        assertThat(actual.facilitatorUserId()).isEqualTo("alice");
        assertThat(actual.slotCount()).isEqualTo(3);
        assertThat(actual.recordingUrl()).isEqualTo("https://example.test/rec.mp4");
    }

    @Test
    void get_countsZeroSlotsWhenTheSessionHasNoSections() {

        // given
        var session = Session.builder().id(102L).communityId(7L).status(SessionStatus.SCHEDULED).displayTitle("Session")
                .build();
        when(sessionService.get(102L)).thenReturn(session);

        // when
        var actual = sut.get(102L);

        // then
        assertThat(actual.title()).isEqualTo("Session");
        assertThat(actual.slotCount()).isZero();
    }
}
