package dev.axonia.bookmarks.internal;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.events.Section;
import dev.axonia.events.Session;
import dev.axonia.events.SessionService;
import dev.axonia.events.SessionStatus;
import dev.axonia.events.Slot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class SessionAccessAdapterTest {

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private SessionAccessAdapter sut;

    @Test
    void get_resolvesTitleFromFirstNonBlankSlotTitleAndSumsSlotsAcrossSections() {

        // given a session whose first section's first slot has a blank title, but a later slot is titled
        var scheduledAt = Instant.parse("2026-04-15T18:00:00Z");
        var occurrenceDate = LocalDate.of(2026, 4, 15);
        var firstSection = Section.builder().slots(
                java.util.List.of(Slot.builder().title("  ").build(), Slot.builder().title("Live Coding").build()))
                .build();
        var secondSection = Section.builder().slots(java.util.List.of(Slot.builder().title(null).build())).build();
        var session = Session.builder().id(100L).communityId(7L).status(SessionStatus.SCHEDULED)
                .scheduledAt(scheduledAt).occurrenceDate(occurrenceDate).facilitatorUserId("alice")
                .recordingUrl("https://example.test/rec.mp4").sections(java.util.List.of(firstSection, secondSection))
                .build();
        when(sessionService.get(100L)).thenReturn(session);

        // when
        var actual = sut.get(100L);

        // then the first non-blank slot title wins, and slotCount sums slots across both sections
        assertThat(actual.id()).isEqualTo(100L);
        assertThat(actual.communityId()).isEqualTo(7L);
        assertThat(actual.title()).isEqualTo("Live Coding");
        assertThat(actual.status()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(actual.scheduledAt()).isEqualTo(scheduledAt);
        assertThat(actual.occurrenceDate()).isEqualTo(occurrenceDate);
        assertThat(actual.facilitatorUserId()).isEqualTo("alice");
        assertThat(actual.slotCount()).isEqualTo(3);
        assertThat(actual.recordingUrl()).isEqualTo("https://example.test/rec.mp4");
    }

    @Test
    void get_fallsBackToGenericTitleWhenNoSlotHasATitle() {

        // given sections/slots exist but none carries a non-blank title
        var section = Section.builder()
                .slots(java.util.List.of(Slot.builder().title("").build(), Slot.builder().title(null).build())).build();
        var session = Session.builder().id(101L).communityId(7L).status(SessionStatus.HELD)
                .sections(java.util.List.of(section)).build();
        when(sessionService.get(101L)).thenReturn(session);

        // when
        var actual = sut.get(101L);

        // then
        assertThat(actual.title()).isEqualTo("Session");
        assertThat(actual.slotCount()).isEqualTo(2);
    }

    @Test
    void get_fallsBackToGenericTitleAndZeroSlotsWhenSessionHasNoSections() {

        // given
        var session = Session.builder().id(102L).communityId(7L).status(SessionStatus.SCHEDULED).build();
        when(sessionService.get(102L)).thenReturn(session);

        // when
        var actual = sut.get(102L);

        // then
        assertThat(actual.title()).isEqualTo("Session");
        assertThat(actual.slotCount()).isZero();
    }
}
