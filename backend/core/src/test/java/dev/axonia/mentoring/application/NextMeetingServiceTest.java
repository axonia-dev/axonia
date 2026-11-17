package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.domain.EndReason;
import dev.axonia.mentoring.domain.MentoringGuild;
import dev.axonia.mentoring.domain.NextMeeting;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;
import dev.axonia.shared.api.error.ValidationException;

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
class NextMeetingServiceTest {

    private static final String ORG = "org-1";
    private static final long PAIRING = 90L;
    private static final long COMMUNITY = 7L;
    private static final String MIA = "mia";
    private static final String NOAH = "noah";
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00.123456Z");
    private static final Instant AT = Instant.parse("2026-11-16T10:00:00.123Z");
    private static final LocalDate THURSDAY = LocalDate.parse("2026-11-19");

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
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

    private NextMeetingService service;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(NOAH);
        when(memberClocks.zoneOf(anyString(), anyString())).thenReturn(ZoneId.of("UTC"));
        when(communityAccess.guildOf(ORG, COMMUNITY)).thenReturn(new MentoringGuild("platform", "Platform"));
        when(personDirectory.displayName(ORG, NOAH)).thenReturn("Noah");
        when(personDirectory.displayName(ORG, MIA)).thenReturn("Mia");
        service = new NextMeetingService(new PairingParties(organizationContext, currentUser, pairings), pairings,
                memberClocks, communityAccess, personDirectory, notices, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Pairing.PairingBuilder pairing() {

        return Pairing.builder().id(PAIRING).communityId(COMMUNITY).mentorUserId(MIA).menteeUserId(NOAH)
                .startedAt(Instant.parse("2026-11-01T09:00:00Z"));
    }

    private void store(Pairing pairing) {

        when(pairings.findForWrite(ORG, PAIRING)).thenReturn(Optional.of(pairing));
    }

    @Test
    void eitherPersonSetsTheDateAndThePartnerIsTold() {

        // given
        store(pairing().build());

        // when
        service.set(PAIRING, THURSDAY);

        // then
        verify(pairings).setNextMeeting(ORG, PAIRING, new NextMeeting(THURSDAY, NOAH, AT));
        verify(notices).meetingSet(
                pairing().nextMeetingOn(THURSDAY).nextMeetingSetBy(NOAH).nextMeetingSetAt(AT).build(),
                new MentoringGuild("platform", "Platform"), "Noah", MIA);
    }

    @Test
    void theMentorSetsItAndTheMenteeIsTold() {

        // given
        when(currentUser.userId()).thenReturn(MIA);
        store(pairing().build());

        // when
        service.set(PAIRING, THURSDAY);

        // then
        verify(notices).meetingSet(pairing().nextMeetingOn(THURSDAY).nextMeetingSetBy(MIA).nextMeetingSetAt(AT).build(),
                new MentoringGuild("platform", "Platform"), "Mia", NOAH);
    }

    @Test
    void settingTheSameDateAgainWritesAndTellsNothing() {

        // given
        store(pairing().nextMeetingOn(THURSDAY).nextMeetingSetBy(MIA).nextMeetingSetAt(AT.minusSeconds(60)).build());

        // when
        service.set(PAIRING, THURSDAY);

        // then
        verify(pairings, never()).setNextMeeting(any(), anyLong(), any());
        verifyNoInteractions(notices);
    }

    @Test
    void changingTheDateTellsThePartnerAgain() {

        // given
        var friday = THURSDAY.plusDays(1);
        store(pairing().nextMeetingOn(THURSDAY).nextMeetingSetBy(MIA).nextMeetingSetAt(AT.minusSeconds(60)).build());

        // when
        service.set(PAIRING, friday);

        // then
        verify(pairings).setNextMeeting(ORG, PAIRING, new NextMeeting(friday, NOAH, AT));
        verify(notices).meetingSet(pairing().nextMeetingOn(friday).nextMeetingSetBy(NOAH).nextMeetingSetAt(AT).build(),
                new MentoringGuild("platform", "Platform"), "Noah", MIA);
    }

    @Test
    void clearingTellsNobody() {

        // given
        store(pairing().nextMeetingOn(THURSDAY).nextMeetingSetBy(MIA).nextMeetingSetAt(AT).build());

        // when
        service.clear(PAIRING);

        // then
        verify(pairings).clearNextMeeting(ORG, PAIRING);
        verifyNoInteractions(notices);
    }

    @Test
    void aDateBeforeTodayInTheCallersZoneIsRefused() {

        // given - 10:00 UTC is already the 17th on Kiritimati
        store(pairing().build());
        when(memberClocks.zoneOf(ORG, NOAH)).thenReturn(ZoneId.of("Pacific/Kiritimati"));

        // when / then
        assertThatThrownBy(() -> service.set(PAIRING, LocalDate.parse("2026-11-16")))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "MENTORING_MEETING_PAST");
        verify(pairings, never()).setNextMeeting(any(), anyLong(), any());
    }

    @Test
    void todayIsAllowed() {

        // given
        store(pairing().build());

        // when
        service.set(PAIRING, LocalDate.parse("2026-11-16"));

        // then
        verify(pairings).setNextMeeting(ORG, PAIRING, new NextMeeting(LocalDate.parse("2026-11-16"), NOAH, AT));
    }

    @Test
    void anErasedPartnerIsToldNothing() {

        // given
        store(pairing().mentorUserId(null).build());

        // when
        service.set(PAIRING, THURSDAY);

        // then
        verify(pairings).setNextMeeting(ORG, PAIRING, new NextMeeting(THURSDAY, NOAH, AT));
        verifyNoInteractions(notices);
    }

    @Test
    void anEndedPairingIsRefused() {

        // given
        store(pairing().endedAt(NOW.minusSeconds(5)).endReason(EndReason.MENTOR).build());

        // when / then
        assertThatThrownBy(() -> service.set(PAIRING, THURSDAY)).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "MENTORING_PAIRING_ENDED");
        assertThatThrownBy(() -> service.clear(PAIRING)).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "MENTORING_PAIRING_ENDED");
        verify(pairings, never()).clearNextMeeting(any(), anyLong());
        verifyNoInteractions(notices);
    }

    @Test
    void aStrangerGetsNotFound() {

        // given
        store(pairing().mentorUserId("lea").menteeUserId("zoe").build());

        // when / then
        assertThatThrownBy(() -> service.set(PAIRING, THURSDAY)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.clear(PAIRING)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.set(404L, THURSDAY)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(notices);
    }

    @Test
    void aDelegatedSessionIsRefused() {

        // given
        when(currentUser.delegated()).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.set(PAIRING, THURSDAY)).isInstanceOf(OwnSignInRequiredException.class);
        assertThatThrownBy(() -> service.clear(PAIRING)).isInstanceOf(OwnSignInRequiredException.class);
        verifyNoInteractions(pairings, notices);
    }
}
