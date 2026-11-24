package dev.axonia.mentoring.adapter.out.communication;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.communication.api.MessageType;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.MeetingRepository;
import dev.axonia.mentoring.application.port.out.MentorListingRepository;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.application.port.out.ProposalRepository;
import dev.axonia.mentoring.domain.ListingSwitch;
import dev.axonia.mentoring.domain.MentorHistory;
import dev.axonia.mentoring.domain.MentorListing;
import dev.axonia.mentoring.domain.MentoringSubject;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.PlannedMeeting;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalStatus;
import dev.axonia.mentoring.domain.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@MockitoSettings
class MentoringSubjectPolicyTest {

    private static final String ORG = "org-1";
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    @Mock
    private ProposalRepository proposals;
    @Mock
    private PairingRepository pairings;
    @Mock
    private MeetingRepository meetings;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private PersonDirectory personDirectory;
    @Mock
    private MentorListingRepository listings;

    private MentoringSubjectPolicy policy;

    @BeforeEach
    void setUp() {

        policy = new MentoringSubjectPolicy(proposals, pairings, meetings, listings, communityAccess, personDirectory,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Proposal proposal(long id, ProposalStatus status, Instant createdAt) {

        return Proposal.builder().id(id).communityId(7L).mentorUserId("mia").menteeUserId("noah")
                .proposedBy(Role.MENTEE).topics(List.of("Kafka")).message("Hi").status(status).createdAt(createdAt)
                .build();
    }

    private static String proposalRef(long id) {

        return MentoringSubject.proposal(id, "noah").ref();
    }

    @Test
    void handlesExactlyTheMentoringTypes() {

        assertThat(policy.handles()).containsExactlyInAnyOrder(MessageType.of("mentoring_proposal"),
                MessageType.of("mentoring_declined"), MessageType.of("mentoring_ended"),
                MessageType.of("mentoring_words_asked"), MessageType.of("mentoring_words_received"),
                MessageType.of("mentoring_meeting_set"), MessageType.of("mentoring_meeting_reminder"),
                MessageType.of("mentoring_proposal_reopened"), MessageType.of("mentoring_listing_ask"),
                MessageType.of("mentoring_listed"));
    }

    private static Pairing.PairingBuilder ongoing() {

        return Pairing.builder().id(90L).communityId(7L).mentorUserId("mia").menteeUserId("noah");
    }

    private static PlannedMeeting plannedAt(Instant at) {

        return PlannedMeeting.builder().id(12L).pairingId(90L).on(LocalDate.parse("2026-11-26")).topicIds(List.of())
                .plannedBy("noah").plannedAt(at).build();
    }

    private static String meetingRef(String kind, Instant plannedAt) {

        return MentoringSubject.meeting(12L, kind + "-" + plannedAt.toEpochMilli(), "noah").ref();
    }

    @Test
    void aPlannedOrMovedNoticeIsOwedWhileThatPlanningStands() {

        // given
        when(meetings.find(ORG, 12L)).thenReturn(Optional.of(plannedAt(NOW)));
        when(pairings.find(ORG, 90L)).thenReturn(Optional.of(ongoing().build()));

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET, meetingRef("planned", NOW))).isTrue();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET, meetingRef("moved", NOW))).isTrue();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET, meetingRef("other", NOW))).isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET,
                MentoringSubject.meeting(12L, String.valueOf(NOW.toEpochMilli()), "noah").ref())).isFalse();
    }

    @Test
    void aMovedOrRemovedMeetingSupersedesItsNotice() {

        // given
        when(meetings.find(ORG, 12L)).thenReturn(Optional.of(plannedAt(NOW.plusSeconds(60))));
        when(meetings.find(ORG, 13L)).thenReturn(Optional.empty());
        lenient().when(pairings.find(ORG, 90L)).thenReturn(Optional.of(ongoing().build()));

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET, meetingRef("planned", NOW))).isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET,
                MentoringSubject.meeting(13L, "planned-" + NOW.toEpochMilli(), "noah").ref())).isFalse();
    }

    @Test
    void anEndedOrPurgedPairingsMeetingIsOwedNothing() {

        // given
        when(meetings.find(ORG, 12L)).thenReturn(Optional.of(plannedAt(NOW)));
        when(meetings.find(ORG, 14L))
                .thenReturn(Optional.of(plannedAt(NOW).toBuilder().id(14L).pairingId(94L).build()));
        when(pairings.find(ORG, 90L)).thenReturn(Optional.of(ongoing().endedAt(NOW.plusSeconds(5)).build()));
        when(pairings.find(ORG, 94L)).thenReturn(Optional.empty());

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET, meetingRef("planned", NOW))).isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET,
                MentoringSubject.meeting(14L, "planned-" + NOW.toEpochMilli(), "noah").ref())).isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_REMINDER, reminderRef("2026-11-26")))
                .isFalse();
    }

    @Test
    void aLegacyPairingSubjectMeetingNoticeIsOwedNothing() {

        // given
        lenient().when(pairings.find(ORG, 90L)).thenReturn(Optional.of(ongoing().build()));

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET,
                MentoringSubject.pairing(90L, String.valueOf(NOW.toEpochMilli()), "noah").ref())).isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_REMINDER,
                MentoringSubject.pairing(90L, "2026-11-19", "mia").ref())).isFalse();
    }

    private static String reminderRef(String day) {

        return MentoringSubject.meeting(12L, day + "." + NOW.toEpochMilli(), "mia").ref();
    }

    @Test
    void aReminderIsOwedWhileTheMeetingStaysOnThatDayAndUnheld() {

        // given
        when(meetings.find(ORG, 12L)).thenReturn(Optional.of(plannedAt(NOW)));
        when(meetings.find(ORG, 13L)).thenReturn(Optional.of(plannedAt(NOW).toBuilder().id(13L).held(true).build()));
        when(meetings.find(ORG, 14L)).thenReturn(Optional.empty());
        lenient().when(pairings.find(ORG, 90L)).thenReturn(Optional.of(ongoing().build()));

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_REMINDER, reminderRef("2026-11-26")))
                .isTrue();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_REMINDER, reminderRef("2026-11-27")))
                .isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_REMINDER,
                MentoringSubject.meeting(13L, "2026-11-26." + NOW.toEpochMilli(), "mia").ref())).isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_REMINDER,
                MentoringSubject.meeting(14L, "2026-11-26." + NOW.toEpochMilli(), "mia").ref())).isFalse();
    }

    @Test
    void aReminderOfAnEarlierPlanningOfTheSameDayIsOwedNothing() {

        // given: moved away and back, so planned again since the reminder was queued
        when(meetings.find(ORG, 12L)).thenReturn(Optional.of(plannedAt(NOW.plusSeconds(60))));
        lenient().when(pairings.find(ORG, 90L)).thenReturn(Optional.of(ongoing().build()));

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_REMINDER, reminderRef("2026-11-26")))
                .isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_REMINDER,
                MentoringSubject.meeting(12L, "2026-11-26." + NOW.plusSeconds(60).toEpochMilli(), "mia").ref()))
                .isTrue();
    }

    @Test
    void aPlannedNoticeOfAMeetingHeldOrPassedIsOwedNothing() {

        // given: today is 2026-11-16 in UTC
        when(meetings.find(ORG, 12L)).thenReturn(Optional.of(plannedAt(NOW).toBuilder().held(true).build()));
        when(meetings.find(ORG, 13L))
                .thenReturn(Optional.of(plannedAt(NOW).toBuilder().id(13L).on(LocalDate.parse("2026-11-15")).build()));
        when(meetings.find(ORG, 14L))
                .thenReturn(Optional.of(plannedAt(NOW).toBuilder().id(14L).on(LocalDate.parse("2026-11-16")).build()));
        when(pairings.find(ORG, 90L)).thenReturn(Optional.of(ongoing().build()));

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET, meetingRef("planned", NOW))).isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET,
                MentoringSubject.meeting(13L, "planned-" + NOW.toEpochMilli(), "noah").ref())).isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET,
                MentoringSubject.meeting(14L, "moved-" + NOW.toEpochMilli(), "noah").ref())).isTrue();
    }

    @Test
    void aProposalNoticeIsOwedWhileTheProposalIsOpen() {

        // given
        when(proposals.find(ORG, 1L)).thenReturn(Optional.of(proposal(1L, ProposalStatus.OPEN, NOW)));
        when(proposals.find(ORG, 2L)).thenReturn(Optional.of(proposal(2L, ProposalStatus.WITHDRAWN, NOW)));
        when(proposals.find(ORG, 3L))
                .thenReturn(Optional.of(proposal(3L, ProposalStatus.OPEN, NOW.minus(Duration.ofDays(30)))));
        when(proposals.find(ORG, 4L)).thenReturn(Optional.empty());

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_PROPOSAL, proposalRef(1L))).isTrue();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_PROPOSAL, proposalRef(2L))).isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_PROPOSAL, proposalRef(3L))).isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_PROPOSAL, proposalRef(4L))).isFalse();
    }

    @Test
    void aDeclineIsOwedWhileTheProposalStaysDeclined() {

        // given
        when(proposals.find(ORG, 1L)).thenReturn(Optional.of(proposal(1L, ProposalStatus.DECLINED, NOW)));
        when(proposals.find(ORG, 2L)).thenReturn(Optional.of(proposal(2L, ProposalStatus.OPEN, NOW)));
        when(proposals.find(ORG, 4L)).thenReturn(Optional.empty());

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_DECLINED, proposalRef(1L))).isTrue();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_DECLINED, proposalRef(2L))).isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_DECLINED, proposalRef(4L))).isFalse();
    }

    @Test
    void aDeclineNamingItsMomentIsOwedOnlyByThatVeryAnswer() {

        // given
        var answeredAt = NOW.minusSeconds(60);
        when(proposals.find(ORG, 1L))
                .thenReturn(Optional.of(proposal(1L, ProposalStatus.DECLINED, NOW.minusSeconds(600)).toBuilder()
                        .answeredAt(answeredAt).build()));
        var thisDecline = MentoringSubject.proposal(1L, String.valueOf(answeredAt.toEpochMilli()), "mia").ref();
        var anEarlierDecline = MentoringSubject
                .proposal(1L, String.valueOf(NOW.minusSeconds(300).toEpochMilli()), "mia").ref();

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_DECLINED, thisDecline)).isTrue();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_DECLINED, anEarlierDecline)).isFalse();
    }

    @Test
    void aReopeningIsOwedWhileTheProposalIsOpen() {

        // given
        var reopened = proposal(1L, ProposalStatus.OPEN, NOW.minus(Duration.ofDays(40))).toBuilder()
                .reopenedAt(NOW.minus(Duration.ofDays(1))).build();
        when(proposals.find(ORG, 1L)).thenReturn(Optional.of(reopened));
        when(proposals.find(ORG, 2L)).thenReturn(
                Optional.of(reopened.toBuilder().id(2L).status(ProposalStatus.DECLINED).answeredAt(NOW).build()));
        var ref = MentoringSubject.proposal(1L, String.valueOf(NOW.toEpochMilli()), "mia").ref();
        var declinedAgain = MentoringSubject.proposal(2L, String.valueOf(NOW.toEpochMilli()), "mia").ref();

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_PROPOSAL_REOPENED, ref)).isTrue();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_PROPOSAL_REOPENED, declinedAgain)).isFalse();
    }

    @Test
    void aRetiredAskForWordsStillWaitingIsOwedNothing() {

        // given: the pairing is still there
        lenient().when(pairings.find(ORG, 90L)).thenReturn(Optional.of(ongoing().build()));

        // when / then
        assertThat(
                policy.isEnabledFor(ORG, MessageType.MENTORING_WORDS_ASKED, MentoringSubject.pairing(90L, "mia").ref()))
                .isFalse();
    }

    @Test
    void aPairingNoticeIsOwedWhileThePairingExists() {

        // given
        when(pairings.find(ORG, 90L)).thenReturn(Optional.of(Pairing.builder().id(90L).build()));
        when(pairings.find(ORG, 91L)).thenReturn(Optional.empty());

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_ENDED, MentoringSubject.pairing(90L, "mia").ref()))
                .isTrue();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_ENDED, MentoringSubject.pairing(91L, "mia").ref()))
                .isFalse();
    }

    @Test
    void aProposalsReceiverMustStillBeInTheGuild() {

        // given
        when(proposals.find(ORG, 1L)).thenReturn(Optional.of(proposal(1L, ProposalStatus.OPEN, NOW)));
        when(proposals.find(ORG, 4L)).thenReturn(Optional.empty());
        when(communityAccess.isMember(ORG, 7L, "mia")).thenReturn(true);
        when(communityAccess.isMember(ORG, 7L, "left")).thenReturn(false);
        when(communityAccess.isMember(ORG, 7L, "off")).thenReturn(true);
        when(personDirectory.isPresent(ORG, "mia")).thenReturn(true);
        when(personDirectory.isPresent(ORG, "off")).thenReturn(false);

        // when / then
        assertThat(policy.isStillAMember(ORG, proposalRef(1L), "mia")).isTrue();
        assertThat(policy.isStillAMember(ORG, proposalRef(1L), "left")).isFalse();
        assertThat(policy.isStillAMember(ORG, proposalRef(1L), "off")).isFalse();
        assertThat(policy.isStillAMember(ORG, proposalRef(4L), "mia")).isFalse();
    }

    @Test
    void aPairingsSideNeedsOnlyToBeInTheOrganization() {

        // given
        when(personDirectory.isPresent(ORG, "noah")).thenReturn(true);
        var ref = MentoringSubject.pairing(90L, "mia").ref();

        // when / then
        assertThat(policy.isStillAMember(ORG, ref, "noah")).isTrue();
        assertThat(policy.isStillAMember(ORG, ref, "gone")).isFalse();
    }

    private void mentorOf(MentorHistory history, ListingSwitch state) {

        when(pairings.mentorHistoriesIn(ORG, 7L)).thenReturn(List.of(history));
        when(listings.in(ORG, 7L)).thenReturn(state == ListingSwitch.DEFAULT
                ? Map.of()
                : Map.of(history.mentorId(), new MentorListing(7L, history.mentorId(), state, NOW, null)));
    }

    @Test
    void theAskIsWithdrawnOnceTheSwitchIsSet() {

        // given
        var ref = MentoringSubject.listing(7L, "mia").ref();
        var unshownAlone = new MentorHistory("mia", false, false, true);

        // when / then
        mentorOf(unshownAlone, ListingSwitch.DEFAULT);
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_LISTING_ASK, ref)).isTrue();
        mentorOf(unshownAlone, ListingSwitch.ON);
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_LISTING_ASK, ref)).isFalse();
        mentorOf(unshownAlone, ListingSwitch.OFF);
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_LISTING_ASK, ref)).isFalse();
    }

    @Test
    void theToldNoticeIsWithdrawnOnceOff() {

        // given
        var ref = MentoringSubject.listing(7L, "mia").ref();
        var shown = new MentorHistory("mia", false, true, false);

        // when / then
        mentorOf(shown, ListingSwitch.DEFAULT);
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_LISTED, ref)).isTrue();
        mentorOf(shown, ListingSwitch.OFF);
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_LISTED, ref)).isFalse();
    }

    @Test
    void aListingNoticeOfAMentorWithNoHistoryLeftIsOwedNothing() {

        // given
        when(pairings.mentorHistoriesIn(ORG, 7L)).thenReturn(List.of(new MentorHistory("noah", true, false, false)));
        var ref = MentoringSubject.listing(7L, "mia").ref();

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_LISTING_ASK, ref)).isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_LISTED, ref)).isFalse();
    }

    @Test
    void aListingNoticesMentorMustStillBeInTheGuild() {

        // given
        when(communityAccess.isMember(ORG, 7L, "mia")).thenReturn(true);
        when(communityAccess.isMember(ORG, 7L, "left")).thenReturn(false);
        when(communityAccess.isMember(ORG, 7L, "off")).thenReturn(true);
        when(personDirectory.isPresent(ORG, "mia")).thenReturn(true);
        when(personDirectory.isPresent(ORG, "off")).thenReturn(false);
        var ref = MentoringSubject.listing(7L, "mia").ref();

        // when / then
        assertThat(policy.isStillAMember(ORG, ref, "mia")).isTrue();
        assertThat(policy.isStillAMember(ORG, ref, "left")).isFalse();
        assertThat(policy.isStillAMember(ORG, ref, "off")).isFalse();
    }
}
