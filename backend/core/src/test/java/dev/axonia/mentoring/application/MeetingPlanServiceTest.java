package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import dev.axonia.mentoring.api.MeetingRequest;
import dev.axonia.mentoring.api.PlannedMeetingView;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.JournalRepository;
import dev.axonia.mentoring.application.port.out.MeetingRepository;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.MentoringNotices.MeetingNotice;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.domain.EndReason;
import dev.axonia.mentoring.domain.MentoringGuild;
import dev.axonia.mentoring.domain.MentoringPerson;
import dev.axonia.mentoring.domain.Note;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.PlannedMeeting;
import dev.axonia.mentoring.domain.Topic;
import dev.axonia.mentoring.domain.TopicState;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeetingPlanServiceTest {

    private static final String ORG = "org-1";
    private static final long PAIRING = 90L;
    private static final long COMMUNITY = 7L;
    private static final String MIA = "mia";
    private static final String NOAH = "noah";
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00.123456Z");
    private static final Instant AT = Instant.parse("2026-11-18T10:00:00.123Z");
    private static final Instant EARLIER = Instant.parse("2026-11-10T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-11-18");
    private static final LocalDate THURSDAY = LocalDate.parse("2026-11-26");
    private static final MentoringGuild GUILD = new MentoringGuild("platform", "Platform");

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private PairingRepository pairings;
    @Mock
    private MeetingRepository meetings;
    @Mock
    private JournalRepository journal;
    @Mock
    private MemberClocks memberClocks;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private PersonDirectory personDirectory;
    @Mock
    private MentoringNotices notices;

    private MeetingPlanService service;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(NOAH);
        when(memberClocks.zoneOf(anyString(), anyString())).thenReturn(ZoneId.of("UTC"));
        when(communityAccess.guildOf(ORG, COMMUNITY)).thenReturn(GUILD);
        when(personDirectory.shortName(ORG, NOAH)).thenReturn("Noah");
        when(personDirectory.resolve(List.of(NOAH)))
                .thenReturn(Map.of(NOAH, new MentoringPerson(NOAH, "Noah Brandt", "Noah", null)));
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(
                List.of(topic(3L, "Helm", TopicState.TODO, 0), topic(7L, "Kafka basics", TopicState.IN_PROGRESS, 0),
                        topic(8L, "Feedback", TopicState.TODO, 1), topic(9L, "Sizing", TopicState.DONE, 0),
                        topic(10L, "Tracing", TopicState.TODO, 2), topic(11L, "Ownership", TopicState.TODO, 3)));
        when(meetings.insert(any(), any())).thenReturn(12L);
        service = new MeetingPlanService(new PairingParties(organizationContext, currentUser, pairings), meetings,
                journal, memberClocks, communityAccess, personDirectory, notices, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Topic topic(long id, String label, TopicState state, int position) {

        return Topic.builder().id(id).pairingId(PAIRING).label(label).state(state).position(position).build();
    }

    private static Pairing.PairingBuilder pairing() {

        return Pairing.builder().id(PAIRING).communityId(COMMUNITY).mentorUserId(MIA).menteeUserId(NOAH)
                .startedAt(Instant.parse("2026-11-01T09:00:00Z"));
    }

    private static PlannedMeeting.PlannedMeetingBuilder meeting(long id, LocalDate on) {

        return PlannedMeeting.builder().id(id).pairingId(PAIRING).on(on).topicIds(List.of(3L)).plannedBy(MIA)
                .plannedAt(EARLIER);
    }

    private void store(Pairing pairing, PlannedMeeting... planned) {

        when(pairings.findForWrite(ORG, PAIRING)).thenReturn(Optional.of(pairing));
        when(meetings.meetingsOf(ORG, PAIRING)).thenReturn(List.of(planned));
        for (var meeting : planned) {
            when(meetings.find(ORG, meeting.id())).thenReturn(Optional.of(meeting));
        }
    }

    @Test
    void planningTellsThePartnerWithTheFirstTopic() {

        // given
        store(pairing().build());

        // when
        var actual = service.plan(PAIRING, new MeetingRequest(THURSDAY, List.of(3L, 7L)));

        // then
        var planned = PlannedMeeting.builder().pairingId(PAIRING).on(THURSDAY).topicIds(List.of(3L, 7L)).plannedBy(NOAH)
                .plannedAt(AT).build();
        verify(meetings).insert(ORG, planned);
        verify(notices).meetingPlanned(
                MeetingNotice.builder().pairing(pairing().build()).meeting(planned.toBuilder().id(12L).build())
                        .guild(GUILD).byName("Noah").firstTopic("Kafka basics").recipientId(MIA).build());
        assertThat(actual).isEqualTo(PlannedMeetingView.builder().id(12L).on(THURSDAY).topicIds(List.of(3L, 7L))
                .plannedById(NOAH).plannedByName("Noah Brandt").passed(false).build());
    }

    @Test
    void aMeetingWithoutTopicsIsToldWithoutOne() {

        // given
        store(pairing().build());

        // when
        service.plan(PAIRING, new MeetingRequest(THURSDAY, null));

        // then
        verify(notices).meetingPlanned(MeetingNotice
                .builder().pairing(pairing().build()).meeting(PlannedMeeting.builder().id(12L).pairingId(PAIRING)
                        .on(THURSDAY).topicIds(List.of()).plannedBy(NOAH).plannedAt(AT).build())
                .guild(GUILD).byName("Noah").recipientId(MIA).build());
    }

    @Test
    void aDayWithNotesIsRefusedAsHeldBeforeTaken() {

        // given: today holds a held meeting, and a later day a note no meeting planned
        store(pairing().build(), meeting(1L, TODAY).held(true).build(), meeting(2L, THURSDAY).build());
        when(journal.notesOf(ORG, PAIRING)).thenReturn(List.of(note(TODAY), note(THURSDAY.plusDays(2))));

        // when / then
        assertThatThrownBy(() -> service.plan(PAIRING, new MeetingRequest(TODAY, List.of())))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_HELD")
                .hasMessage("That day already has its notes.");
        assertThatThrownBy(() -> service.change(PAIRING, 2L, new MeetingRequest(TODAY, List.of())))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_HELD");
        assertThatThrownBy(() -> service.change(PAIRING, 2L, new MeetingRequest(THURSDAY.plusDays(2), List.of())))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_HELD");
        verify(meetings, never()).insert(any(), any());
        verify(meetings, never()).update(any(), any());
        verifyNoInteractions(notices);
    }

    private static Note note(LocalDate on) {

        return Note.builder().id(on.toEpochDay()).pairingId(PAIRING).authorUserId(MIA).body("Met").meetingOn(on)
                .writtenAt(NOW).build();
    }

    @Test
    void aDayAlreadyPlannedIsRefused() {

        // given
        store(pairing().build(), meeting(1L, THURSDAY).build());

        // when / then
        assertThatThrownBy(() -> service.plan(PAIRING, new MeetingRequest(THURSDAY, List.of())))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_TAKEN");
        verify(meetings, never()).insert(any(), any());
        verifyNoInteractions(notices);
    }

    @Test
    void theThirteenthIsRefusedUnderTheLock() {

        // given
        var planned = new ArrayList<PlannedMeeting>();
        for (var i = 0; i < 12; i++) {
            planned.add(meeting(i + 1L, TODAY.plusDays(i)).build());
        }
        planned.add(meeting(20L, TODAY.minusDays(1)).build());
        planned.add(meeting(21L, TODAY.minusDays(2)).build());
        planned.add(meeting(22L, TODAY.minusDays(3)).held(true).build());
        store(pairing().build(), planned.toArray(PlannedMeeting[]::new));

        // when / then
        assertThatThrownBy(() -> service.plan(PAIRING, new MeetingRequest(TODAY.plusDays(30), List.of())))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_LIMIT");
        verify(pairings).findForWrite(ORG, PAIRING);
        verify(pairings, never()).find(any(), anyLong());
        verify(meetings, never()).insert(any(), any());
    }

    @Test
    void sixTopicsAreRefused() {

        // given
        store(pairing().build());

        // when / then
        assertThatThrownBy(() -> service.plan(PAIRING, new MeetingRequest(THURSDAY, List.of(3L, 7L, 8L, 9L, 10L, 11L))))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_TOPICS");
        verify(meetings, never()).insert(any(), any());
    }

    @Test
    void aTopicOfAnotherPairingIsRefused() {

        // given
        store(pairing().build(), meeting(1L, THURSDAY).build());

        // when / then
        assertThatThrownBy(() -> service.plan(PAIRING, new MeetingRequest(THURSDAY.plusDays(7), List.of(3L, 404L))))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_TOPIC_GONE");
        assertThatThrownBy(() -> service.change(PAIRING, 1L, new MeetingRequest(THURSDAY, List.of(404L))))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_TOPIC_GONE");
        verify(meetings, never()).insert(any(), any());
        verify(meetings, never()).update(any(), any());
    }

    @Test
    void aPastOrFarDayIsRefused() {

        // given
        store(pairing().build(), meeting(1L, THURSDAY).build());

        // when / then
        assertThatThrownBy(() -> service.plan(PAIRING, new MeetingRequest(TODAY.minusDays(1), List.of())))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_PAST");
        assertThatThrownBy(() -> service.change(PAIRING, 1L, new MeetingRequest(TODAY.plusDays(367), List.of())))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_TOO_FAR");
    }

    @Test
    void changingOnlyTopicsTellsNobodyAndKeepsPlannedBy() {

        // given
        store(pairing().build(), meeting(1L, THURSDAY).build());

        // when
        service.change(PAIRING, 1L, new MeetingRequest(THURSDAY, List.of(7L, 8L)));

        // then
        verify(meetings).update(ORG, meeting(1L, THURSDAY).topicIds(List.of(7L, 8L)).build());
        verifyNoInteractions(notices);
    }

    @Test
    void movingTellsThePartnerAndMakesTheMoverThePlanner() {

        // given
        var original = meeting(1L, THURSDAY).build();
        store(pairing().build(), original);
        var friday = THURSDAY.plusDays(1);

        // when
        service.change(PAIRING, 1L, new MeetingRequest(friday, List.of(8L, 7L)));

        // then
        var moved = meeting(1L, friday).topicIds(List.of(8L, 7L)).plannedBy(NOAH).plannedAt(AT).build();
        verify(meetings).update(ORG, moved);
        verify(notices).withdrawMeetingPlanned(original);
        verify(notices).withdrawMeetingMoved(original);
        verify(notices).meetingMoved(MeetingNotice.builder().pairing(pairing().build()).meeting(moved).guild(GUILD)
                .byName("Noah").firstTopic("Kafka basics").recipientId(MIA).build());
        verify(notices, never()).meetingPlanned(any());
    }

    @Test
    void movingOntoAPlannedDayIsRefused() {

        // given
        store(pairing().build(), meeting(1L, THURSDAY).build(), meeting(2L, THURSDAY.plusDays(7)).build());

        // when / then
        assertThatThrownBy(() -> service.change(PAIRING, 1L, new MeetingRequest(THURSDAY.plusDays(7), List.of())))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_TAKEN");
        verify(meetings, never()).update(any(), any());
    }

    @Test
    void movingMyOwnMeetingBeforeItsNoticeLeftReannouncesThePlan() {

        // given
        var original = meeting(1L, THURSDAY).plannedBy(NOAH).build();
        store(pairing().build(), original);
        when(notices.withdrawMeetingPlanned(original)).thenReturn(true);
        var friday = THURSDAY.plusDays(1);

        // when
        service.change(PAIRING, 1L, new MeetingRequest(friday, List.of()));

        // then
        var moved = meeting(1L, friday).topicIds(List.of()).plannedBy(NOAH).plannedAt(AT).build();
        verify(notices).meetingPlanned(MeetingNotice.builder().pairing(pairing().build()).meeting(moved).guild(GUILD)
                .byName("Noah").recipientId(MIA).build());
        verify(notices, never()).meetingMoved(any());
        verify(notices, never()).withdrawMeetingMoved(any());
    }

    @Test
    void movingThePartnersMeetingBeforeItsNoticeLeftIsAMove() {

        // given: Mia planned it, her notice to Noah still waits, and Noah moves it
        var original = meeting(1L, THURSDAY).build();
        store(pairing().build(), original);
        when(notices.withdrawMeetingPlanned(original)).thenReturn(true);
        var friday = THURSDAY.plusDays(1);

        // when
        service.change(PAIRING, 1L, new MeetingRequest(friday, List.of()));

        // then
        var moved = meeting(1L, friday).topicIds(List.of()).plannedBy(NOAH).plannedAt(AT).build();
        verify(notices).withdrawMeetingPlanned(original);
        verify(notices).meetingMoved(MeetingNotice.builder().pairing(pairing().build()).meeting(moved).guild(GUILD)
                .byName("Noah").recipientId(MIA).build());
        verify(notices, never()).meetingPlanned(any());
    }

    @Test
    void aDayTakenByARacingWriteIsRefusedAsTaken() {

        // given
        store(pairing().build(), meeting(1L, THURSDAY).build());
        when(meetings.insert(any(), any())).thenThrow(new DuplicateKeyException("mentoring_meeting_day_key"));
        org.mockito.Mockito.doThrow(new DuplicateKeyException("mentoring_meeting_day_key")).when(meetings).update(any(),
                any());

        // when / then
        assertThatThrownBy(() -> service.plan(PAIRING, new MeetingRequest(THURSDAY.plusDays(7), List.of())))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_TAKEN")
                .hasMessage("A meeting is already planned for that day.");
        assertThatThrownBy(() -> service.change(PAIRING, 1L, new MeetingRequest(THURSDAY.plusDays(8), List.of())))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_TAKEN");
        verifyNoInteractions(notices);
    }

    @Test
    void aSecondMoveBeforeTheNoticeLeftStaysAMove() {

        // given: the plan's notice left, the first move's still waits
        var movedOnce = meeting(1L, THURSDAY).build();
        store(pairing().build(), movedOnce);
        when(notices.withdrawMeetingPlanned(movedOnce)).thenReturn(false);
        when(notices.withdrawMeetingMoved(movedOnce)).thenReturn(true);
        var friday = THURSDAY.plusDays(1);

        // when
        service.change(PAIRING, 1L, new MeetingRequest(friday, List.of()));

        // then
        var movedTwice = meeting(1L, friday).topicIds(List.of()).plannedBy(NOAH).plannedAt(AT).build();
        verify(notices).withdrawMeetingMoved(movedOnce);
        verify(notices).meetingMoved(MeetingNotice.builder().pairing(pairing().build()).meeting(movedTwice).guild(GUILD)
                .byName("Noah").recipientId(MIA).build());
        verify(notices, never()).meetingPlanned(any());
    }

    @Test
    void aPassedMeetingCannotBeChanged() {

        // given
        store(pairing().build(), meeting(1L, TODAY.minusDays(1)).build(), meeting(2L, TODAY).held(true).build());

        // when / then
        assertThatThrownBy(() -> service.change(PAIRING, 1L, new MeetingRequest(THURSDAY, List.of())))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_PASSED")
                .hasMessage("This meeting has passed.");
        assertThatThrownBy(() -> service.change(PAIRING, 2L, new MeetingRequest(THURSDAY, List.of())))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_PASSED");
        verify(meetings, never()).update(any(), any());
    }

    @Test
    void aMeetingOfAnotherPairingIsNotFound() {

        // given
        store(pairing().build());
        when(meetings.find(ORG, 5L)).thenReturn(Optional.of(meeting(5L, THURSDAY).pairingId(91L).build()));

        // when / then
        assertThatThrownBy(() -> service.change(PAIRING, 5L, new MeetingRequest(THURSDAY, List.of())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.remove(PAIRING, 5L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.remove(PAIRING, 6L)).isInstanceOf(NotFoundException.class);
        verify(meetings, never()).delete(any(), anyLong(), anyLong());
    }

    @Test
    void aPassedMeetingCanBeRemoved() {

        // given
        var passed = meeting(1L, TODAY.minusDays(1)).build();
        store(pairing().build(), passed);

        // when
        service.remove(PAIRING, 1L);

        // then
        verify(meetings).delete(ORG, PAIRING, 1L);
    }

    @Test
    void aHeldMeetingCannotBeRemoved() {

        // given
        store(pairing().build(), meeting(1L, TODAY).held(true).build());

        // when / then
        assertThatThrownBy(() -> service.remove(PAIRING, 1L)).isInstanceOf(NotFoundException.class);
        verify(meetings, never()).delete(any(), anyLong(), anyLong());
        verifyNoInteractions(notices);
    }

    @Test
    void removingWithdrawsAnUnsentNoticeAndTellsNobody() {

        // given
        var planned = meeting(1L, THURSDAY).build();
        store(pairing().build(), planned);

        // when
        service.remove(PAIRING, 1L);

        // then
        verify(meetings).delete(ORG, PAIRING, 1L);
        verify(notices).withdrawMeetingPlanned(planned);
        verify(notices).withdrawMeetingMoved(planned);
        verify(notices, never()).meetingPlanned(any());
        verify(notices, never()).meetingMoved(any());
    }

    @Test
    void anEndedPairingTakesNoMeeting() {

        // given
        store(pairing().endedAt(NOW.minusSeconds(5)).endReason(EndReason.MENTOR).build(),
                meeting(1L, THURSDAY).build());

        // when / then
        assertThatThrownBy(() -> service.plan(PAIRING, new MeetingRequest(THURSDAY, List.of())))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "MENTORING_PAIRING_ENDED");
        assertThatThrownBy(() -> service.change(PAIRING, 1L, new MeetingRequest(THURSDAY, List.of())))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "MENTORING_PAIRING_ENDED");
        assertThatThrownBy(() -> service.remove(PAIRING, 1L)).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "MENTORING_PAIRING_ENDED");
        verifyNoInteractions(meetings, notices);
    }

    @Test
    void aDelegatedSessionPlansNothing() {

        // given
        when(currentUser.delegated()).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.plan(PAIRING, new MeetingRequest(THURSDAY, List.of())))
                .isInstanceOf(OwnSignInRequiredException.class);
        assertThatThrownBy(() -> service.change(PAIRING, 1L, new MeetingRequest(THURSDAY, List.of())))
                .isInstanceOf(OwnSignInRequiredException.class);
        assertThatThrownBy(() -> service.remove(PAIRING, 1L)).isInstanceOf(OwnSignInRequiredException.class);
        verifyNoInteractions(pairings, meetings, notices);
    }

    @Test
    void aPartnerErasedIsToldNothing() {

        // given
        store(pairing().mentorUserId(null).build(), meeting(1L, THURSDAY).build());

        // when
        service.plan(PAIRING, new MeetingRequest(THURSDAY.plusDays(7), List.of(3L)));
        service.change(PAIRING, 1L, new MeetingRequest(THURSDAY.plusDays(1), List.of(3L)));

        // then
        verify(meetings).insert(any(), any());
        verify(meetings).update(any(), any());
        verifyNoInteractions(notices);
    }

    @Test
    void theMeetingDaysAreReadInTheCallersZone() {

        // given - 10:00 UTC is already the 19th on Kiritimati
        store(pairing().build());
        when(memberClocks.zoneOf(ORG, NOAH)).thenReturn(ZoneId.of("Pacific/Kiritimati"));

        // when / then
        assertThatThrownBy(() -> service.plan(PAIRING, new MeetingRequest(TODAY, List.of())))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_PAST");
    }
}
