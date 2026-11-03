package dev.axonia.events.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;

import dev.axonia.events.api.SlotCleared;
import dev.axonia.events.api.spi.SessionDeletionParticipant;
import dev.axonia.events.application.port.out.ActionItemStore;
import dev.axonia.events.application.port.out.AttendanceStore;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.SectionRecord;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SessionRecordMother;
import dev.axonia.events.domain.SlotRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class SessionDeletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T09:00:00Z");

    @Mock
    private SessionStore sessionStore;
    @Mock
    private AttendanceStore attendanceStore;
    @Mock
    private ActionItemStore actionItemStore;
    @Mock
    private TopicLifecycle topicLifecycle;
    @Mock
    private SessionAuthorizer sessionAuthorizer;
    @Mock
    private SessionDeletionParticipant participant;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SessionDeletionService sut;

    @BeforeEach
    void wire() {

        sut = new SessionDeletionService(sessionStore, attendanceStore, actionItemStore, topicLifecycle,
                sessionAuthorizer, List.of(participant), currentUser, organizationContext, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void stored(SessionRecord session) {

        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("orga");
        when(sessionStore.findByOrganizationIdAndId("default", session.getId())).thenReturn(Optional.of(session));
    }

    @Test
    void delete() {

        // given
        var session = SessionRecordMother.any().id(7L).communityId(1L).seriesId(null).status("SCHEDULED")
                .sections(List.of()).build();
        stored(session);

        // when
        sut.delete(7L);

        // then - everything pointing at the session goes before the session itself
        InOrder order = inOrder(participant, attendanceStore, actionItemStore, sessionStore);
        order.verify(participant).sessionDeleted("default", 1L, 7L);
        order.verify(attendanceStore).deleteAllForSession("default", 7L);
        order.verify(actionItemStore).clearPinsFor("default", 1L, 7L, NOW);
        order.verify(sessionStore).delete(session);
    }

    @Test
    void delete_releasesEverySlotAsARemovedSlotWould() {

        // given
        var talk = SlotRecord.builder().id(11L).topicId(99L).build();
        var recorded = SlotRecord.builder().id(12L).build();
        var session = SessionRecordMother.any().id(7L).communityId(1L).seriesId(null).status("SCHEDULED")
                .sections(List.of(SectionRecord.builder().id(21L).slots(List.of(talk)).build(),
                        SectionRecord.builder().id(22L).slots(List.of(recorded)).build()))
                .build();
        stored(session);

        // when
        sut.delete(7L);

        // then
        verify(topicLifecycle).release("default", 99L);
        verify(topicLifecycle).release("default", null);
        verify(eventPublisher).publishEvent(new SlotCleared(11L, 1L));
        verify(eventPublisher).publishEvent(new SlotCleared(12L, 1L));
    }

    @Test
    void delete_cancelledSessionAllowed() {

        // given
        var session = SessionRecordMother.any().id(7L).communityId(1L).seriesId(null).status("CANCELLED")
                .sections(List.of()).build();
        stored(session);

        // when
        sut.delete(7L);

        // then
        verify(sessionStore).delete(session);
    }

    @Test
    void delete_cancelledSessionLeavesItsFormerTopicsWhereTheyAreNow() {

        // given - cancelling already sent the topic back to the backlog, from where it may since have been rescheduled
        var slot = SlotRecord.builder().id(11L).topicId(99L).build();
        var session = SessionRecordMother.any().id(7L).communityId(1L).seriesId(null).status("CANCELLED")
                .sections(List.of(SectionRecord.builder().id(21L).slots(List.of(slot)).build())).build();
        stored(session);

        // when
        sut.delete(7L);

        // then
        verifyNoInteractions(topicLifecycle);
        verify(eventPublisher).publishEvent(new SlotCleared(11L, 1L));
        verify(sessionStore).delete(session);
    }

    @Test
    void delete_heldRejected() {

        // given
        var session = SessionRecordMother.any().id(7L).communityId(1L).seriesId(null).status("HELD").sections(List.of())
                .build();
        stored(session);

        // when / then
        assertThatThrownBy(() -> sut.delete(7L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("SESSION_ALREADY_HELD");
        verify(sessionStore, never()).delete(any());
        verifyNoInteractions(participant, attendanceStore, actionItemStore);
    }

    @Test
    void delete_seriesOccurrenceRejected() {

        // given
        var session = SessionRecordMother.any().id(7L).communityId(1L).seriesId(3L).status("SCHEDULED")
                .sections(List.of()).build();
        stored(session);

        // when / then
        assertThatThrownBy(() -> sut.delete(7L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("SESSION_IN_SERIES");
        verify(sessionStore, never()).delete(any());
    }

    @Test
    void delete_nonOrganizerRejected() {

        // given
        var session = SessionRecordMother.any().id(7L).communityId(1L).seriesId(null).status("SCHEDULED")
                .sections(List.of()).build();
        stored(session);
        doThrow(new ForbiddenException("Only an organizer may do this")).when(sessionAuthorizer)
                .requireOrganizer(session, "orga");

        // when / then
        assertThatThrownBy(() -> sut.delete(7L)).isInstanceOf(ForbiddenException.class);
        verify(sessionStore, never()).delete(any());
        verify(participant, never()).sessionDeleted(anyString(), anyLong(), anyLong());
    }

    @Test
    void delete_missing() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionStore.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.delete(7L)).isInstanceOf(NotFoundException.class);
    }
}
