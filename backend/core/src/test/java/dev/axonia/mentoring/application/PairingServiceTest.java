package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.mentoring.api.MentoringPresence;
import dev.axonia.mentoring.api.NextMeetingDay;
import dev.axonia.mentoring.api.PairingSummary;
import dev.axonia.mentoring.api.ProposalView;
import dev.axonia.mentoring.application.port.out.AvailabilityRepository;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.JournalRepository;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.application.port.out.ProposalRepository;
import dev.axonia.mentoring.domain.EndReason;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.MentoringGuild;
import dev.axonia.mentoring.domain.MentoringPerson;
import dev.axonia.mentoring.domain.NoteTally;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalStatus;
import dev.axonia.mentoring.domain.Role;
import dev.axonia.mentoring.domain.Topic;
import dev.axonia.mentoring.domain.TopicState;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PairingServiceTest {

    private static final String ORG = "org-1";
    private static final long PLATFORM = 7L;
    private static final long DATA = 8L;
    private static final String ME = "max";
    private static final String MIA = "mia";
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00.123456Z");
    private static final Instant TODAY = Instant.parse("2026-11-16T10:00:00.123Z");

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private PairingRepository pairings;
    @Mock
    private ProposalRepository proposals;
    @Mock
    private JournalRepository journal;
    @Mock
    private AvailabilityRepository availabilities;
    @Mock
    private PersonDirectory personDirectory;
    @Mock
    private MentoringNotices notices;
    @Mock
    private MemberClocks memberClocks;

    private PairingService service;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(ME);
        when(communityAccess.communityById(PLATFORM))
                .thenReturn(new MentoringCommunity(PLATFORM, "platform", "Platform", false, true));
        when(communityAccess.communityById(DATA)).thenReturn(new MentoringCommunity(DATA, "data", "Data", true, false));
        when(communityAccess.guildOf(ORG, PLATFORM)).thenReturn(new MentoringGuild("platform", "Platform"));
        when(communityAccess.guildOf(ORG, DATA)).thenReturn(new MentoringGuild("data", "Data"));
        when(personDirectory.isPresent(ORG, ME)).thenReturn(true);
        when(memberClocks.zoneOf(ORG, ME)).thenReturn(ZoneOffset.UTC);
        when(pairings.end(eq(ORG), anyLong(), any(), any())).thenReturn(true);
        when(personDirectory.shortName(eq(ORG), any())).thenAnswer(inv -> name(inv.getArgument(1)));
        when(personDirectory.resolve(anyCollection())).thenAnswer(inv -> {
            Collection<String> ids = inv.getArgument(0);
            return ids.stream()
                    .collect(Collectors.toMap(id -> id, id -> new MentoringPerson(id, name(id), null, null)));
        });
        service = new PairingService(organizationContext, currentUser, communityAccess, pairings, proposals, journal,
                availabilities, personDirectory, notices, memberClocks, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String name(String id) {

        return id.substring(0, 1).toUpperCase() + id.substring(1);
    }

    private static Pairing.PairingBuilder pairing(long id, long community, String mentor, String mentee) {

        return Pairing.builder().id(id).communityId(community).mentorUserId(mentor).menteeUserId(mentee)
                .startedAt(NOW.minus(Duration.ofDays(20)));
    }

    private void store(Pairing pairing) {

        when(pairings.find(ORG, pairing.id())).thenReturn(Optional.of(pairing));
    }

    @Test
    void eitherPersonEndsWithoutAReason() {

        // given
        var asMentee = pairing(90L, PLATFORM, MIA, ME).build();
        var asMentor = pairing(91L, PLATFORM, ME, MIA).build();
        store(asMentee);
        store(asMentor);

        // when
        service.end(90L);
        service.end(91L);

        // then
        verify(pairings).end(ORG, 90L, EndReason.MENTEE, TODAY);
        verify(pairings).end(ORG, 91L, EndReason.MENTOR, TODAY);
        verify(notices).ended(asMentee, new MentoringGuild("platform", "Platform"), "Max", MIA);
        verify(notices).ended(asMentor, new MentoringGuild("platform", "Platform"), "Max", MIA);
    }

    @Test
    void aStrangerCannotEndAPairingAndLearnsNothing() {

        // given
        store(pairing(90L, PLATFORM, MIA, "noah").build());

        // when / then
        assertThatThrownBy(() -> service.end(90L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.end(404L)).isInstanceOf(NotFoundException.class);
        verify(pairings, never()).end(any(), anyLong(), any(), any());
        verifyNoInteractions(notices);
    }

    @Test
    void endingTwiceIsRefused() {

        // given
        store(pairing(90L, PLATFORM, MIA, ME).endedAt(NOW.minusSeconds(5)).endReason(EndReason.MENTOR).build());

        // when / then
        assertThatThrownBy(() -> service.end(90L)).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("MENTORING_PAIRING_ENDED"));
        verify(pairings, never()).end(any(), anyLong(), any(), any());
    }

    @Test
    void losingTheRaceToEndReadsAsAlreadyEndedAndTellsNobody() {

        // given
        store(pairing(90L, PLATFORM, MIA, ME).build());
        when(pairings.end(ORG, 90L, EndReason.MENTEE, TODAY)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> service.end(90L)).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("MENTORING_PAIRING_ENDED"));
        verifyNoInteractions(notices);
    }

    @Test
    void aDepartureFindingThePairingAlreadyEndedSkipsItSilently() {

        // given
        var endedMeanwhile = pairing(90L, PLATFORM, MIA, "leo").build();
        var ongoing = pairing(91L, PLATFORM, "leo", "noah").build();
        when(pairings.ongoingInvolving(ORG, PLATFORM, "leo")).thenReturn(List.of(endedMeanwhile, ongoing));
        when(pairings.end(ORG, 90L, EndReason.LEFT, TODAY)).thenReturn(false);

        // when
        service.departedFromGuild(ORG, PLATFORM, "leo");

        // then
        verify(notices, never()).ended(eq(endedMeanwhile), any(), any(), any());
        verify(notices).ended(ongoing, new MentoringGuild("platform", "Platform"), "Leo", "noah");
    }

    @Test
    void aDelegatedSessionEndsNothing() {

        // given
        when(currentUser.delegated()).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.end(90L)).isInstanceOf(OwnSignInRequiredException.class);
    }

    @Test
    void aDelegatedSessionReadsNothingOfMyMentoring() {

        // given
        when(currentUser.delegated()).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.mine()).isInstanceOf(OwnSignInRequiredException.class);
        verifyNoInteractions(pairings, proposals, personDirectory);
    }

    @Test
    void endingDoesNotConsultTheGuildSoASwitchedOffOrArchivedOneKeepsItsPairingsEndable() {

        // given
        var switchedOff = pairing(90L, DATA, MIA, ME).build();
        store(switchedOff);

        // when
        service.end(90L);

        // then
        verify(pairings).end(ORG, 90L, EndReason.MENTEE, TODAY);
        verify(notices).ended(switchedOff, new MentoringGuild("data", "Data"), "Max", MIA);
    }

    @Test
    void endingWithAnErasedPartnerTellsNobody() {

        // given
        store(pairing(90L, PLATFORM, null, ME).build());

        // when
        service.end(90L);

        // then
        verify(pairings).end(ORG, 90L, EndReason.MENTEE, TODAY);
        verifyNoInteractions(notices);
    }

    @Test
    void leavingTheGuildEndsPairingsThereWithdrawsProposalsAndClearsAvailability() {

        // given
        var asMentee = pairing(90L, PLATFORM, MIA, "leo").build();
        var asMentor = pairing(91L, PLATFORM, "leo", "noah").build();
        when(pairings.ongoingInvolving(ORG, PLATFORM, "leo")).thenReturn(List.of(asMentee, asMentor));

        // when
        service.departedFromGuild(ORG, PLATFORM, "leo");

        // then
        var order = org.mockito.Mockito.inOrder(pairings);
        order.verify(pairings).lockMember(ORG, PLATFORM, "leo");
        order.verify(pairings).ongoingInvolving(ORG, PLATFORM, "leo");
        verify(pairings).end(ORG, 90L, EndReason.LEFT, TODAY);
        verify(pairings).end(ORG, 91L, EndReason.LEFT, TODAY);
        verify(notices).ended(asMentee, new MentoringGuild("platform", "Platform"), "Leo", MIA);
        verify(notices).ended(asMentor, new MentoringGuild("platform", "Platform"), "Leo", "noah");
        verify(proposals).withdrawOpenInvolving(ORG, PLATFORM, "leo", TODAY);
        verify(availabilities).deleteIn(ORG, PLATFORM, "leo");
        verify(pairings, never()).ongoingInvolving(ORG, DATA, "leo");
    }

    @Test
    void deactivationEndsEveryOngoingPairingOfThePersonInEveryGuild() {

        // given
        var platform = pairing(90L, PLATFORM, MIA, "leo").build();
        var data = pairing(91L, DATA, "leo", "noah").build();
        when(pairings.ongoingInvolving(ORG, null, "leo")).thenReturn(List.of(platform, data));
        when(communityAccess.communityIdsOf(ORG, "leo")).thenReturn(List.of(DATA, PLATFORM));

        // when
        service.deactivated(ORG, "leo");

        // then
        var order = org.mockito.Mockito.inOrder(pairings);
        order.verify(pairings).lockMember(ORG, PLATFORM, "leo");
        order.verify(pairings).lockMember(ORG, DATA, "leo");
        order.verify(pairings).ongoingInvolving(ORG, null, "leo");
        verify(pairings).end(ORG, 90L, EndReason.DEACTIVATED, TODAY);
        verify(pairings).end(ORG, 91L, EndReason.DEACTIVATED, TODAY);
        verify(notices).ended(platform, new MentoringGuild("platform", "Platform"), "Leo", MIA);
        verify(notices).ended(data, new MentoringGuild("data", "Data"), "Leo", "noah");
        verify(proposals).withdrawOpenInvolving(ORG, null, "leo", TODAY);
    }

    @Test
    void mineListsPairingsAcrossEveryGuildAndOutlivesLeaving() {

        // given
        var ongoing = pairing(90L, PLATFORM, MIA, ME).build();
        var ended = pairing(91L, DATA, ME, "noah").endedAt(NOW.minusSeconds(100)).endReason(EndReason.LEFT).build();
        var endedLater = pairing(92L, DATA, ME, "leo").endedAt(NOW.minusSeconds(10)).endReason(EndReason.MENTOR)
                .build();
        when(pairings.involving(ORG, ME)).thenReturn(List.of(ongoing, ended, endedLater));
        when(communityAccess.inAMentoringGuild(ORG, ME)).thenReturn(false);

        // when
        var actual = service.mine();

        // then
        assertThat(actual.inAMentoringGuild()).isFalse();
        assertThat(actual.ongoing()).extracting(PairingSummary::id, PairingSummary::myRole, PairingSummary::partnerName,
                PairingSummary::communityName).containsExactly(tuple(90L, "MENTEE", "Mia", "Platform"));
        assertThat(actual.ended()).extracting(PairingSummary::id, PairingSummary::myRole, PairingSummary::endedAt)
                .containsExactly(tuple(92L, "MENTOR", NOW.minusSeconds(10)),
                        tuple(91L, "MENTOR", NOW.minusSeconds(100)));
        verify(communityAccess).communityById(DATA);
    }

    @Test
    void myMentoringGlancesAtEachPairingFromTheCallersOwnDay() {

        // given: 10:00 UTC on the 16th is already the 17th in Kiritimati
        var walked = pairing(90L, PLATFORM, MIA, ME).nextMeetingOn(LocalDate.parse("2026-11-16")).build();
        var bare = pairing(91L, PLATFORM, ME, "noah").build();
        when(pairings.involving(ORG, ME)).thenReturn(List.of(walked, bare));
        when(memberClocks.zoneOf(ORG, ME)).thenReturn(ZoneId.of("Pacific/Kiritimati"));
        when(journal.trailsOf(ORG, List.of(90L, 91L))).thenReturn(Map.of(90L, List.of(topic("Done", TopicState.DONE),
                topic("Now", TopicState.IN_PROGRESS), topic("Later", TopicState.TODO))));
        when(journal.tallyOf(ORG, List.of(90L, 91L)))
                .thenReturn(Map.of(90L, new NoteTally(3, 2, LocalDate.parse("2026-11-09"))));

        // when
        var actual = service.mine();

        // then
        assertThat(actual.today()).isEqualTo(LocalDate.parse("2026-11-17"));
        assertThat(actual.ongoing()).extracting(PairingSummary::id, PairingSummary::stops, PairingSummary::nowTopic,
                PairingSummary::nextMeeting, PairingSummary::meetings, PairingSummary::notes, PairingSummary::lastMetOn)
                .containsExactly(tuple(90L, List.of("DONE", "IN_PROGRESS", "TODO"), "Now",
                        new NextMeetingDay(LocalDate.parse("2026-11-16"), true), 2, 3, LocalDate.parse("2026-11-09")),
                        tuple(91L, List.of(), null, null, 0, 0, null));
        verify(journal).trailsOf(ORG, List.of(90L, 91L));
        verify(journal).tallyOf(ORG, List.of(90L, 91L));
    }

    @Test
    void mineSplitsProposalsIntoReceivedAndSent() {

        // given
        var received = proposal(1L, MIA, ME, Role.MENTOR).build();
        var sent = proposal(2L, ME, "noah", Role.MENTOR).build();
        when(proposals.involving(ORG, ME)).thenReturn(List.of(received, sent));
        when(communityAccess.inAMentoringGuild(ORG, ME)).thenReturn(true);

        // when
        var actual = service.mine();

        // then
        assertThat(actual.inAMentoringGuild()).isTrue();
        assertThat(actual.received()).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo(1L);
            assertThat(v.partnerName()).isEqualTo("Mia");
            assertThat(v.proposedBy()).isEqualTo("MENTOR");
        });
        assertThat(actual.sent()).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo(2L);
            assertThat(v.partnerName()).isEqualTo("Noah");
        });
    }

    private static Proposal declinedByMe(long id, Duration ago) {

        return proposal(id, MIA, ME, Role.MENTOR).createdAt(NOW.minus(ago).minusSeconds(60))
                .status(ProposalStatus.DECLINED).answeredAt(NOW.minus(ago)).build();
    }

    @Test
    void aDeclineIsReopenableFromMyMentoringForThirtyDays() {

        // given
        var recent = declinedByMe(1L, Duration.ofDays(5));
        var old = declinedByMe(2L, Duration.ofDays(30)).toBuilder().mentorUserId("noah").build();
        var erased = declinedByMe(3L, Duration.ofDays(1)).toBuilder().mentorUserId(null).build();
        when(proposals.involving(ORG, ME)).thenReturn(List.of(recent, old, erased));

        // when
        var actual = service.mine();

        // then
        assertThat(actual.received()).extracting(ProposalView::id, ProposalView::reopenableUntil).containsExactly(
                tuple(1L, NOW.minus(Duration.ofDays(5)).plus(Duration.ofDays(30))), tuple(2L, null), tuple(3L, null));
    }

    @Test
    void reopenIsNotOfferedOverTheSendersNewerAnswer() {

        // given: Mia's proposal declined, then mine to Mia declined by her
        var mine = proposal(2L, ME, MIA, Role.MENTOR).createdAt(NOW.minus(Duration.ofDays(4)))
                .status(ProposalStatus.DECLINED).answeredAt(NOW.minus(Duration.ofDays(3))).build();
        var elsewhere = proposal(3L, ME, MIA, Role.MENTOR).communityId(DATA).build();
        when(proposals.involving(ORG, ME)).thenReturn(List.of(mine, declinedByMe(1L, Duration.ofDays(5)), elsewhere));

        // when
        var actual = service.mine();

        // then
        assertThat(actual.received()).singleElement().extracting(ProposalView::reopenableUntil).isNull();
    }

    @Test
    void reopenIsNotOfferedOnceTheTwoPairedSinceTheDecline() {

        // given
        when(proposals.involving(ORG, ME)).thenReturn(List.of(declinedByMe(1L, Duration.ofDays(5))));
        when(pairings.involving(ORG, ME)).thenReturn(List.of(
                pairing(90L, PLATFORM, ME, MIA).startedAt(NOW.minus(Duration.ofDays(4)))
                        .endedAt(NOW.minus(Duration.ofDays(2))).endReason(EndReason.MENTOR).build(),
                pairing(91L, DATA, ME, MIA).build(), pairing(92L, PLATFORM, ME, "noah").build()));

        // when
        var actual = service.mine();

        // then
        assertThat(actual.received()).singleElement().extracting(ProposalView::reopenableUntil).isNull();
    }

    @Test
    void proposalsReadAsLapsedAfter30Days() {

        // given
        var old = proposal(1L, MIA, ME, Role.MENTOR).createdAt(NOW.minus(Duration.ofDays(31))).build();
        when(proposals.involving(ORG, ME)).thenReturn(List.of(old));

        // when
        var actual = service.mine();

        // then
        assertThat(actual.received()).singleElement().extracting(v -> v.status()).isEqualTo("LAPSED");
    }

    @Test
    void anErasedPartnerReadsAsDeletedMember() {

        // given
        when(pairings.involving(ORG, ME)).thenReturn(List
                .of(pairing(90L, PLATFORM, null, ME).endedAt(NOW.minusSeconds(5)).endReason(EndReason.MENTOR).build()));
        when(proposals.involving(ORG, ME))
                .thenReturn(List.of(proposal(1L, null, ME, Role.MENTEE).status(ProposalStatus.DECLINED).build()));

        // when
        var actual = service.mine();

        // then
        assertThat(actual.ended()).singleElement().satisfies(s -> {
            assertThat(s.partnerName()).isEqualTo("Deleted member");
            assertThat(s.partnerAvatarUrl()).isNull();
            assertThat(s.partnerId()).isNull();
        });
        assertThat(actual.sent()).singleElement().satisfies(v -> {
            assertThat(v.partnerName()).isEqualTo("Deleted member");
            assertThat(v.partnerId()).isNull();
        });
    }

    @Test
    void aPersonOutsideTheOrganizationReadsNothing() {

        // given
        when(personDirectory.isPresent(ORG, ME)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> service.mine()).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(pairings, proposals);
    }

    private static Proposal.ProposalBuilder proposal(long id, String mentor, String mentee, Role proposedBy) {

        return Proposal.builder().id(id).communityId(PLATFORM).mentorUserId(mentor).menteeUserId(mentee)
                .proposedBy(proposedBy).topics(List.of("Kafka")).message("Hello").status(ProposalStatus.OPEN)
                .createdAt(NOW.minus(Duration.ofDays(2)));
    }

    @Test
    void presenceIsOneQueryForAMemberOfAMentoringGuild() {

        // given
        when(communityAccess.inAMentoringGuild(ORG, ME)).thenReturn(true);

        // when
        var actual = service.presence();

        // then
        assertThat(actual).isEqualTo(new MentoringPresence(true));
        verifyNoInteractions(pairings, proposals);
    }

    @Test
    void presenceOutsideAnyMentoringGuildIsAnyProposalOrPairingOfTheMember() {

        // given
        when(pairings.anyMentoringInvolving(ORG, ME)).thenReturn(true, false);

        // when / then
        assertThat(service.presence()).isEqualTo(new MentoringPresence(true));
        assertThat(service.presence()).isEqualTo(new MentoringPresence(false));
    }

    @Test
    void aDelegatedSessionHasNoPresence() {

        // given
        when(currentUser.delegated()).thenReturn(true);

        // when / then
        assertThatThrownBy(service::presence).isInstanceOf(OwnSignInRequiredException.class);
        verifyNoInteractions(pairings, communityAccess);
    }

    private static Topic topic(String label, TopicState state) {

        return Topic.builder().pairingId(90L).label(label).state(state).build();
    }
}
