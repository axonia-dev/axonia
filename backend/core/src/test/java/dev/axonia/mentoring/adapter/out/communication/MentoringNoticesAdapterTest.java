package dev.axonia.mentoring.adapter.out.communication;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.communication.api.CommunicationPort;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.OutboundMessage;
import dev.axonia.mentoring.application.port.out.MentoringNotices.MeetingReminder;
import dev.axonia.mentoring.domain.MentoringGuild;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalStatus;
import dev.axonia.mentoring.domain.Role;
import dev.axonia.shared.api.web.PublicUrls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class MentoringNoticesAdapterTest {

    private static final String SECRET = "Something only the two of them read";

    @Mock
    private CommunicationPort communication;
    @Mock
    private PublicUrls publicUrls;

    private MentoringNoticesAdapter adapter;

    @BeforeEach
    void setUp() {

        lenient().when(publicUrls.publicBaseUrl()).thenReturn("https://axonia.example");
        adapter = new MentoringNoticesAdapter(communication, publicUrls);
    }

    private OutboundMessage sent() {

        var message = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(communication).enqueue(message.capture());
        return message.getValue();
    }

    private static Pairing pairing() {

        return Pairing.builder().id(90L).communityId(7L).mentorUserId("mia").menteeUserId("noah")
                .startedAt(Instant.parse("2026-11-01T09:00:00Z")).build();
    }

    @Test
    void aProposalIsMailedToTheReceiverAndNamesItsSender() {

        // given
        var proposal = Proposal.builder().id(40L).communityId(7L).mentorUserId("mia").menteeUserId("noah")
                .proposedBy(Role.MENTEE).topics(List.of("Kafka", "Giving feedback")).message(SECRET)
                .status(ProposalStatus.OPEN).createdAt(Instant.parse("2026-11-16T10:00:00Z")).build();

        // when
        adapter.proposed("acme", proposal, new MentoringGuild("platform", "Platform"), "Noah");

        // then
        var actual = sent();
        assertThat(actual.type()).isEqualTo(MessageType.MENTORING_PROPOSAL);
        assertThat(actual.template()).isEqualTo("mentoring-proposal");
        assertThat(actual.inAppOnly()).isFalse();
        assertThat(actual.subjectRef()).isEqualTo("proposal:40:noah");
        assertThat(actual.recipientUserIds()).containsExactly("mia");
        assertThat(actual.aboutUserId()).isEqualTo("noah");
        assertThat(actual.facts()).containsEntry("sessionTitle", "Noah proposes mentoring")
                .containsEntry("line", "Kafka, Giving feedback")
                .containsEntry("topics", List.of("Kafka", "Giving feedback"))
                .containsEntry("mentoringUrl", "https://axonia.example/communities/platform/me/mentoring?org=acme")
                .containsEntry("sender", Map.of("name", "Platform", "initials", "P", "tintBackground", "#f6f0ec",
                        "tintForeground", "#211a26"));
        assertThat(actual.facts().toString()).doesNotContain(SECRET);
    }

    @Test
    void aDeclineTellsTheSenderNotThisTimeWithoutTheMessage() {

        // given
        var proposal = Proposal.builder().id(40L).communityId(7L).mentorUserId("mia").menteeUserId("noah")
                .proposedBy(Role.MENTEE).topics(List.of("Kafka", "Helm")).message(SECRET)
                .status(ProposalStatus.DECLINED).createdAt(Instant.parse("2026-11-16T10:00:00Z"))
                .answeredAt(Instant.ofEpochMilli(1_794_900_000_000L)).build();

        // when
        adapter.declined(proposal, new MentoringGuild("platform", "Platform"), "Mia");

        // then
        var actual = sent();
        assertThat(actual.type()).isEqualTo(MessageType.MENTORING_DECLINED);
        assertThat(actual.inAppOnly()).isTrue();
        assertThat(actual.subjectRef()).isEqualTo("proposal:40@1794900000000:mia");
        assertThat(actual.recipientUserIds()).containsExactly("noah");
        assertThat(actual.aboutUserId()).isEqualTo("mia");
        assertThat(actual.facts()).containsOnlyKeys("sender", "sessionTitle", "line", "mentoringUrl")
                .containsEntry("sessionTitle", "Mia answered: not this time").containsEntry("line", "Kafka")
                .containsEntry("mentoringUrl", "https://axonia.example/communities/platform/me/mentoring");
        assertThat(actual.facts().toString()).doesNotContain(SECRET).doesNotContain("Helm");
    }

    @ParameterizedTest
    @CsvSource({"1, true", "0, false"})
    void withdrawingADeclineTakesBackThatAnswersNoticeAlone(int withdrawn, boolean expected) {

        // given
        var declined = Proposal.builder().id(40L).communityId(7L).mentorUserId("mia").menteeUserId("noah")
                .proposedBy(Role.MENTEE).topics(List.of("Kafka")).status(ProposalStatus.DECLINED)
                .createdAt(Instant.parse("2026-11-16T10:00:00Z")).answeredAt(Instant.ofEpochMilli(1_794_900_000_000L))
                .build();
        when(communication.withdrawPending(MessageType.MENTORING_DECLINED, "proposal:40@1794900000000:mia"))
                .thenReturn(withdrawn);

        // when
        var actual = adapter.withdrawDeclined(declined);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void aReopeningTellsTheSenderInTheirPanelWithoutTheMessage() {

        // given
        var proposal = Proposal.builder().id(40L).communityId(7L).mentorUserId("mia").menteeUserId("noah")
                .proposedBy(Role.MENTEE).topics(List.of("Kafka", "Helm")).message(SECRET).status(ProposalStatus.OPEN)
                .createdAt(Instant.parse("2026-11-16T10:00:00Z")).reopenedAt(Instant.ofEpochMilli(1_794_900_000_000L))
                .build();

        // when
        adapter.reopened(proposal, new MentoringGuild("platform", "Platform"), "Mia");

        // then
        var actual = sent();
        assertThat(actual.type()).isEqualTo(MessageType.MENTORING_PROPOSAL_REOPENED);
        assertThat(actual.inAppOnly()).isTrue();
        assertThat(actual.subjectRef()).isEqualTo("proposal:40@1794900000000:mia");
        assertThat(actual.recipientUserIds()).containsExactly("noah");
        assertThat(actual.aboutUserId()).isEqualTo("mia");
        assertThat(actual.facts()).containsOnlyKeys("sender", "sessionTitle", "line", "mentoringUrl")
                .containsEntry("sessionTitle", "Mia reopened a proposal").containsEntry("line", "Kafka")
                .containsEntry("mentoringUrl", "https://axonia.example/communities/platform/me/mentoring");
        assertThat(actual.facts().toString()).doesNotContain(SECRET).doesNotContain("Helm");
    }

    @Test
    void theEndingNoticeInvitesTheMenteesPostcard() {

        // when
        adapter.ended(pairing(), new MentoringGuild("platform", "Platform"), "Mia", "noah");

        // then
        var actual = sent();
        assertThat(actual.type()).isEqualTo(MessageType.MENTORING_ENDED);
        assertThat(actual.inAppOnly()).isTrue();
        assertThat(actual.subjectRef()).isEqualTo("pairing:90:mia");
        assertThat(actual.recipientUserIds()).containsExactly("noah");
        assertThat(actual.aboutUserId()).isEqualTo("mia");
        assertThat(actual.facts()).containsEntry("sessionTitle", "Mentoring with Mia ended")
                .containsEntry("line", "A postcard to Mia waits in the journal")
                .containsEntry("mentoringUrl", "https://axonia.example/communities/platform/me/mentoring/90");
    }

    @Test
    void theMentorsEndingNoticeIsUnchanged() {

        // when
        adapter.ended(pairing(), new MentoringGuild("platform", "Platform"), "Noah", "mia");

        // then
        var actual = sent();
        assertThat(actual.recipientUserIds()).containsExactly("mia");
        assertThat(actual.aboutUserId()).isEqualTo("noah");
        assertThat(actual.facts()).containsEntry("sessionTitle", "Mentoring with Noah ended").containsEntry("line",
                "The journal stays readable");
    }

    @Test
    void wordsReachTheMentorWithoutTheirText() {

        // when
        adapter.wordsReceived(pairing(), new MentoringGuild("platform", "Platform"), "Noah");

        // then
        var actual = sent();
        assertThat(actual.type()).isEqualTo(MessageType.MENTORING_WORDS_RECEIVED);
        assertThat(actual.inAppOnly()).isTrue();
        assertThat(actual.recipientUserIds()).containsExactly("mia");
        assertThat(actual.aboutUserId()).isEqualTo("noah");
        assertThat(actual.subjectRef()).isEqualTo("pairing:90:noah");
        assertThat(actual.facts()).containsOnlyKeys("sender", "sessionTitle", "line", "mentoringUrl")
                .containsEntry("sessionTitle", "Words from Noah");
    }

    @Test
    void theMeetingNoticeNamesTheDateAndNoNote() {

        // given
        var setAt = Instant.parse("2026-11-16T10:00:00.123Z");
        var pairing = Pairing.builder().id(90L).communityId(7L).mentorUserId("mia").menteeUserId("noah")
                .startedAt(Instant.parse("2026-11-01T09:00:00Z")).nextMeetingOn(LocalDate.parse("2026-11-19"))
                .nextMeetingSetBy("noah").nextMeetingSetAt(setAt).build();

        // when
        adapter.meetingSet(pairing, new MentoringGuild("platform", "Platform"), "Noah", "mia");

        // then
        var actual = sent();
        assertThat(actual.type()).isEqualTo(MessageType.MENTORING_MEETING_SET);
        assertThat(actual.inAppOnly()).isTrue();
        assertThat(actual.recipientUserIds()).containsExactly("mia");
        assertThat(actual.aboutUserId()).isEqualTo("noah");
        assertThat(actual.subjectRef()).isEqualTo("pairing:90@" + setAt.toEpochMilli() + ":noah");
        assertThat(actual.facts()).containsOnlyKeys("sender", "sessionTitle", "line", "mentoringUrl")
                .containsEntry("sessionTitle", "Noah set the next meeting")
                .containsEntry("line", "Thursday 19 November")
                .containsEntry("mentoringUrl", "https://axonia.example/communities/platform/me/mentoring/90");
    }

    @Test
    void eachSideIsRemindedOfTheDayByMailAndInThePanel() {

        // given
        var pairing = Pairing.builder().id(90L).communityId(7L).mentorUserId("mia").menteeUserId("noah")
                .startedAt(Instant.parse("2026-11-01T09:00:00Z")).nextMeetingOn(LocalDate.parse("2026-11-19"))
                .nextMeetingSetBy("noah").nextMeetingSetAt(Instant.parse("2026-11-16T10:00:00Z")).build();

        // when
        adapter.meetingReminder(MeetingReminder.builder().organizationId("acme").pairing(pairing)
                .guild(new MentoringGuild("platform", "Platform")).partnerName("Mia").recipientId("noah").build());

        // then
        var actual = sent();
        assertThat(actual.type()).isEqualTo(MessageType.MENTORING_MEETING_REMINDER);
        assertThat(actual.template()).isEqualTo("mentoring-meeting-reminder");
        assertThat(actual.inAppOnly()).isFalse();
        assertThat(actual.recipientUserIds()).containsExactly("noah");
        assertThat(actual.aboutUserId()).isEqualTo("mia");
        assertThat(actual.subjectRef()).isEqualTo("pairing:90@2026-11-19:mia");
        assertThat(actual.facts()).containsOnlyKeys("sender", "sessionTitle", "line", "mentoringUrl", "partner")
                .containsEntry("sessionTitle", "Mentoring with Mia tomorrow")
                .containsEntry("line", "Thursday 19 November").containsEntry("partner", "Mia")
                .containsEntry("mentoringUrl", "https://axonia.example/communities/platform/me/mentoring/90?org=acme");
    }

    @Test
    void noNoticeIsEverEnqueuedWithoutARecipient() {

        // given
        var erasedMentor = Pairing.builder().id(90L).communityId(7L).menteeUserId("noah")
                .startedAt(Instant.parse("2026-11-01T09:00:00Z")).build();

        // when / then
        assertThatThrownBy(
                () -> adapter.wordsReceived(erasedMentor, new MentoringGuild("platform", "Platform"), "Noah"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(communication);
    }
}
