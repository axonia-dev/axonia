package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.MentoringNotices.MeetingReminder;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.domain.Pairing;

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

        when(communityAccess.nameOf(ORG, 7L)).thenReturn("Platform");
        when(personDirectory.displayName(ORG, MIA)).thenReturn("Mia");
        when(personDirectory.displayName(ORG, NOAH)).thenReturn("Noah");
        zones(ZoneId.of("UTC"), ZoneId.of("UTC"));
    }

    private MeetingReminders at(String instant) {

        return new MeetingReminders(pairings, memberClocks, communityAccess, personDirectory, notices,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private void zones(ZoneId mentor, ZoneId mentee) {

        when(memberClocks.zonesOf(anyString(), anyCollection())).thenReturn(Map.of(MIA, mentor, NOAH, mentee));
    }

    private static Pairing.PairingBuilder pairing() {

        return Pairing.builder().id(90L).communityId(7L).mentorUserId(MIA).menteeUserId(NOAH)
                .startedAt(Instant.parse("2026-11-01T09:00:00Z")).nextMeetingOn(THURSDAY).nextMeetingSetBy(MIA)
                .nextMeetingSetAt(SET_MONDAY);
    }

    private static MeetingReminder reminder(Pairing pairing, String partnerName, String recipientId) {

        return MeetingReminder.builder().organizationId(ORG).pairing(pairing).communityName("Platform")
                .partnerName(partnerName).recipientId(recipientId).build();
    }

    private void meeting(Pairing pairing) {

        when(pairings.withNextMeetingBetween(any(), any(), any())).thenReturn(List.of(pairing));
    }

    @Test
    void eachPersonIsRemindedAtNineOnTheirOwnDayBefore() {

        // given
        var pairing = pairing().build();
        meeting(pairing);
        zones(ZoneId.of("Europe/Zurich"), ZoneId.of("America/New_York"));

        // when
        at("2026-11-18T08:00:00Z").remindDue(ORG);

        // then - 09:00 in Zurich, 03:00 in New York
        verify(notices).meetingReminder(reminder(pairing, "Noah", MIA));
        verifyNoMoreInteractions(notices);

        // and when - 15:00 in Zurich, 09:00 in New York
        at("2026-11-18T14:00:00Z").remindDue(ORG);

        // then
        verify(notices).meetingReminder(reminder(pairing, "Mia", NOAH));
    }

    @Test
    void theWindowReadSpansEveryZonesDayBefore() {

        // given
        meeting(pairing().build());

        // when
        at("2026-11-18T08:00:00Z").remindDue(ORG);

        // then
        verify(pairings).withNextMeetingBetween(ORG, LocalDate.parse("2026-11-18"), LocalDate.parse("2026-11-20"));
    }

    @Test
    void aMemberWithNoZoneIsRemindedOnUtc() {

        // given - the adapter answers UTC for a member with no zone
        var pairing = pairing().build();
        meeting(pairing);

        // when
        at("2026-11-18T08:59:59Z").remindDue(ORG);

        // then
        verifyNoInteractions(notices);

        // and when
        at("2026-11-18T09:00:00Z").remindDue(ORG);

        // then
        verify(notices).meetingReminder(reminder(pairing, "Noah", MIA));
        verify(notices).meetingReminder(reminder(pairing, "Mia", NOAH));
    }

    @Test
    void aDateSetForTomorrowSendsNoReminder() {

        // given
        meeting(pairing().nextMeetingSetAt(Instant.parse("2026-11-18T07:00:00Z")).build());

        // when
        at("2026-11-18T12:00:00Z").remindDue(ORG);

        // then
        verifyNoInteractions(notices);
    }

    @Test
    void anEndedPairingIsNotReminded() {

        // given - the repository reads ongoing pairings only
        when(pairings.withNextMeetingBetween(any(), any(), any())).thenReturn(List.of());

        // when
        at("2026-11-18T12:00:00Z").remindDue(ORG);

        // then
        verifyNoInteractions(notices, memberClocks);
    }

    @Test
    void aChangedDateIsRemindedForTheNewDayOnly() {

        // given
        var friday = pairing().nextMeetingOn(THURSDAY.plusDays(1)).build();
        meeting(friday);

        // when
        at("2026-11-18T12:00:00Z").remindDue(ORG);

        // then
        verifyNoInteractions(notices);

        // and when
        at("2026-11-19T12:00:00Z").remindDue(ORG);

        // then
        verify(notices).meetingReminder(reminder(friday, "Noah", MIA));
        verify(notices).meetingReminder(reminder(friday, "Mia", NOAH));
    }

    @Test
    void anErasedSideIsSkipped() {

        // given
        when(pairings.withNextMeetingBetween(any(), any(), any())).thenReturn(
                List.of(pairing().mentorUserId(null).build(), pairing().id(91L).menteeUserId(null).build()));

        // when
        at("2026-11-18T12:00:00Z").remindDue(ORG);

        // then
        verifyNoInteractions(notices, memberClocks);
        verify(personDirectory, never()).displayName(any(), any());
    }
}
