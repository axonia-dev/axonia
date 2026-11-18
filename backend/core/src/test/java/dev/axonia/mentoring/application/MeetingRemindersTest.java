package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.MeetingRepository;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.MentoringNotices.MeetingReminder;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.domain.MentoringGuild;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.PlannedMeeting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeetingRemindersTest {

    private static final String ORG = "org-1";
    private static final String MIA = "mia";
    private static final String NOAH = "noah";
    private static final LocalDate THURSDAY = LocalDate.parse("2026-11-19");
    private static final Instant SET_MONDAY = Instant.parse("2026-11-16T10:00:00Z");
    private static final MentoringGuild GUILD = new MentoringGuild("platform", "Platform");

    @Mock
    private MeetingRepository meetings;
    @Mock
    private PairingRepository pairings;
    @Mock
    private MemberClocks memberClocks;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private PersonDirectory personDirectory;
    @Mock
    private MentoringNotices notices;

    @BeforeEach
    void setUp() {

        when(communityAccess.guildOf(ORG, 7L)).thenReturn(GUILD);
        when(personDirectory.shortName(ORG, MIA)).thenReturn("Mia");
        when(personDirectory.shortName(ORG, NOAH)).thenReturn("Noah");
        zones(ZoneId.of("UTC"), ZoneId.of("UTC"));
        store(pairing().build());
    }

    private MeetingReminders at(String instant) {

        return new MeetingReminders(meetings, pairings, memberClocks, communityAccess, personDirectory, notices,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private void zones(ZoneId mentor, ZoneId mentee) {

        when(memberClocks.zonesOf(anyString(), anyCollection())).thenReturn(Map.of(MIA, mentor, NOAH, mentee));
    }

    private static Pairing.PairingBuilder pairing() {

        return Pairing.builder().id(90L).communityId(7L).mentorUserId(MIA).menteeUserId(NOAH)
                .startedAt(Instant.parse("2026-11-01T09:00:00Z"));
    }

    private void store(Pairing pairing) {

        when(pairings.find(ORG, pairing.id())).thenReturn(Optional.of(pairing));
    }

    private static PlannedMeeting.PlannedMeetingBuilder meeting(long id, LocalDate on) {

        return PlannedMeeting.builder().id(id).pairingId(90L).on(on).topicIds(List.of()).plannedBy(MIA)
                .plannedAt(SET_MONDAY);
    }

    private static MeetingReminder reminder(PlannedMeeting meeting, String partnerName, String recipientId) {

        return MeetingReminder.builder().organizationId(ORG).pairing(pairing().build()).meeting(meeting).guild(GUILD)
                .partnerName(partnerName).recipientId(recipientId).build();
    }

    private void due(PlannedMeeting... planned) {

        when(meetings.notHeldBetween(any(), any(), any())).thenReturn(List.of(planned));
    }

    @Test
    void eachPersonIsRemindedAtNineOnTheirOwnDayBefore() {

        // given
        var thursday = meeting(1L, THURSDAY).build();
        due(thursday);
        zones(ZoneId.of("Europe/Zurich"), ZoneId.of("America/New_York"));

        // when
        at("2026-11-18T08:00:00Z").remindDue(ORG);

        // then - 09:00 in Zurich, 03:00 in New York
        verify(notices).meetingReminder(reminder(thursday, "Noah", MIA));
        verifyNoMoreInteractions(notices);

        // and when - 15:00 in Zurich, 09:00 in New York
        at("2026-11-18T14:00:00Z").remindDue(ORG);

        // then
        verify(notices).meetingReminder(reminder(thursday, "Mia", NOAH));
    }

    @Test
    void theWindowReadSpansEveryZonesDayBefore() {

        // when
        at("2026-11-18T08:00:00Z").remindDue(ORG);

        // then
        verify(meetings).notHeldBetween(ORG, LocalDate.parse("2026-11-18"), LocalDate.parse("2026-11-20"));
    }

    @Test
    void everyUpcomingMeetingIsRemindedTheDayBefore() {

        // given
        var thursday = meeting(1L, THURSDAY).build();
        var friday = meeting(2L, THURSDAY.plusDays(1)).build();
        var otherPairing = meeting(3L, THURSDAY).pairingId(91L).build();
        store(pairing().id(91L).build());
        due(thursday, friday, otherPairing);

        // when
        at("2026-11-18T12:00:00Z").remindDue(ORG);

        // then
        verify(notices).meetingReminder(reminder(thursday, "Noah", MIA));
        verify(notices).meetingReminder(reminder(thursday, "Mia", NOAH));
        verify(notices).meetingReminder(
                reminder(otherPairing, "Noah", MIA).toBuilder().pairing(pairing().id(91L).build()).build());
        verify(notices).meetingReminder(
                reminder(otherPairing, "Mia", NOAH).toBuilder().pairing(pairing().id(91L).build()).build());
        verifyNoMoreInteractions(notices);
        verify(pairings).find(ORG, 90L);
        verify(pairings).find(ORG, 91L);

        // and when
        at("2026-11-19T12:00:00Z").remindDue(ORG);

        // then
        verify(notices).meetingReminder(reminder(friday, "Noah", MIA));
        verify(notices).meetingReminder(reminder(friday, "Mia", NOAH));
    }

    @Test
    void aMemberWithNoZoneIsRemindedOnUtc() {

        // given - the adapter answers UTC for a member with no zone
        var thursday = meeting(1L, THURSDAY).build();
        due(thursday);

        // when
        at("2026-11-18T08:59:59Z").remindDue(ORG);

        // then
        verifyNoInteractions(notices);

        // and when
        at("2026-11-18T09:00:00Z").remindDue(ORG);

        // then
        verify(notices).meetingReminder(reminder(thursday, "Noah", MIA));
        verify(notices).meetingReminder(reminder(thursday, "Mia", NOAH));
    }

    @Test
    void aMeetingPlannedForTomorrowIsRemindedOfByNobody() {

        // given
        due(meeting(1L, THURSDAY).plannedAt(Instant.parse("2026-11-18T07:00:00Z")).build());

        // when
        at("2026-11-18T12:00:00Z").remindDue(ORG);

        // then
        verifyNoInteractions(notices);
    }

    @Test
    void nothingDueReadsNothingMore() {

        // when - the repository reads meetings no note held, of ongoing pairings only
        at("2026-11-18T12:00:00Z").remindDue(ORG);

        // then
        verifyNoInteractions(notices, memberClocks, pairings);
    }

    @Test
    void anErasedSideOrAPurgedPairingIsSkipped() {

        // given
        store(pairing().mentorUserId(null).build());
        store(pairing().id(91L).menteeUserId(null).build());
        due(meeting(1L, THURSDAY).build(), meeting(2L, THURSDAY).pairingId(91L).build(),
                meeting(3L, THURSDAY).pairingId(92L).build());

        // when
        at("2026-11-18T12:00:00Z").remindDue(ORG);

        // then
        verifyNoInteractions(notices, memberClocks);
        verify(personDirectory, never()).shortName(any(), any());
    }
}
