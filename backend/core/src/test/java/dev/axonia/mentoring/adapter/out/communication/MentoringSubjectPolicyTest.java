package dev.axonia.mentoring.adapter.out.communication;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.communication.api.MessageType;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.application.port.out.ProposalRepository;
import dev.axonia.mentoring.domain.MentoringSubject;
import dev.axonia.mentoring.domain.Pairing;
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
    private CommunityAccess communityAccess;
    @Mock
    private PersonDirectory personDirectory;

    private MentoringSubjectPolicy policy;

    @BeforeEach
    void setUp() {

        policy = new MentoringSubjectPolicy(proposals, pairings, communityAccess, personDirectory,
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
                MessageType.of("mentoring_proposal_reopened"));
    }

    private static Pairing.PairingBuilder meetingSetAt(Instant setAt) {

        return Pairing.builder().id(90L).communityId(7L).mentorUserId("mia").menteeUserId("noah")
                .nextMeetingOn(LocalDate.parse("2026-11-19")).nextMeetingSetBy("noah").nextMeetingSetAt(setAt);
    }

    private static String meetingRef(Instant setAt) {

        return MentoringSubject.pairing(90L, String.valueOf(setAt.toEpochMilli()), "noah").ref();
    }

    @Test
    void aMeetingNoticeIsOwedWhileThatDateIsThePairings() {

        // given
        when(pairings.find(ORG, 90L)).thenReturn(Optional.of(meetingSetAt(NOW).build()));

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET, meetingRef(NOW))).isTrue();
    }

    @Test
    void aSupersededOrClearedMeetingIsOwedNothing() {

        // given
        var later = NOW.plusSeconds(60);
        when(pairings.find(ORG, 90L)).thenReturn(Optional.of(meetingSetAt(later).build()));
        when(pairings.find(ORG, 91L))
                .thenReturn(Optional.of(meetingSetAt(null).id(91L).nextMeetingOn(null).nextMeetingSetBy(null).build()));
        when(pairings.find(ORG, 92L)).thenReturn(Optional.empty());

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET, meetingRef(NOW))).isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET,
                MentoringSubject.pairing(91L, String.valueOf(NOW.toEpochMilli()), "noah").ref())).isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET,
                MentoringSubject.pairing(92L, String.valueOf(NOW.toEpochMilli()), "noah").ref())).isFalse();
    }

    private static String reminderRef(String day) {

        return MentoringSubject.pairing(90L, day, "mia").ref();
    }

    @Test
    void aReminderIsOwedWhileItsDayIsTheNextMeeting() {

        // given
        when(pairings.find(ORG, 90L)).thenReturn(Optional.of(meetingSetAt(NOW).build()));

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_REMINDER, reminderRef("2026-11-19")))
                .isTrue();
    }

    @Test
    void aReminderForAnotherDayIsOwedNothing() {

        // given
        when(pairings.find(ORG, 90L)).thenReturn(Optional.of(meetingSetAt(NOW).build()));
        when(pairings.find(ORG, 91L))
                .thenReturn(Optional.of(meetingSetAt(null).id(91L).nextMeetingOn(null).nextMeetingSetBy(null).build()));

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_REMINDER, reminderRef("2026-11-20")))
                .isFalse();
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_REMINDER,
                MentoringSubject.pairing(91L, "2026-11-19", "mia").ref())).isFalse();
    }

    @Test
    void anEndedPairingsReminderIsOwedNothing() {

        // given
        when(pairings.find(ORG, 90L)).thenReturn(Optional.of(meetingSetAt(NOW).endedAt(NOW.plusSeconds(5)).build()));

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_REMINDER, reminderRef("2026-11-19")))
                .isFalse();
    }

    @Test
    void anEndedPairingsMeetingIsOwedNothing() {

        // given
        when(pairings.find(ORG, 90L)).thenReturn(Optional.of(meetingSetAt(NOW).endedAt(NOW.plusSeconds(5)).build()));

        // when / then
        assertThat(policy.isEnabledFor(ORG, MessageType.MENTORING_MEETING_SET, meetingRef(NOW))).isFalse();
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
        lenient().when(pairings.find(ORG, 90L)).thenReturn(Optional.of(meetingSetAt(NOW).build()));

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
}
