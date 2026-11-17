package dev.axonia.mentoring.configuration;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.api.InAppEntryShape;
import dev.axonia.communication.api.MessageType;

import static org.assertj.core.api.Assertions.assertThat;

class MentoringMessagesTest {

    @Test
    void everyPanelEntryReadsTheLineAndTheMentoringLinkAndIsPrivateToTheMember() {

        // given
        var messages = new MentoringMessages();

        // when / then
        assertThat(messages.mentoringProposalEntry())
                .isEqualTo(new InAppEntryShape(MessageType.MENTORING_PROPOSAL, "line", "mentoringUrl", true));
        assertThat(messages.mentoringDeclinedEntry())
                .isEqualTo(new InAppEntryShape(MessageType.MENTORING_DECLINED, "line", "mentoringUrl", true));
        assertThat(messages.mentoringEndedEntry())
                .isEqualTo(new InAppEntryShape(MessageType.MENTORING_ENDED, "line", "mentoringUrl", true));
        assertThat(messages.mentoringWordsAskedEntry())
                .isEqualTo(new InAppEntryShape(MessageType.MENTORING_WORDS_ASKED, "line", "mentoringUrl", true));
        assertThat(messages.mentoringWordsReceivedEntry())
                .isEqualTo(new InAppEntryShape(MessageType.MENTORING_WORDS_RECEIVED, "line", "mentoringUrl", true));
        assertThat(messages.mentoringMeetingSetEntry())
                .isEqualTo(new InAppEntryShape(MessageType.MENTORING_MEETING_SET, "line", "mentoringUrl", true));
        assertThat(messages.mentoringMeetingReminderEntry())
                .isEqualTo(new InAppEntryShape(MessageType.MENTORING_MEETING_REMINDER, "line", "mentoringUrl", true));
        assertThat(messages.mentoringProposalReopenedEntry())
                .isEqualTo(new InAppEntryShape(MessageType.MENTORING_PROPOSAL_REOPENED, "line", "mentoringUrl", true));
    }
}
