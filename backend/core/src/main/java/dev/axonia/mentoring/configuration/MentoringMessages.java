package dev.axonia.mentoring.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.axonia.communication.api.InAppEntryShape;
import dev.axonia.communication.api.MessageType;
import dev.axonia.mentoring.adapter.out.communication.MentoringNoticesAdapter;

/**
 * The panel entries of the mentoring notices: a short line, and My mentoring or the pairing's journal as the link. The
 * guild is their sender, as on every other community message. Every one is private to the member: a session acting for
 * them sees none of them.
 */
@Configuration(proxyBeanMethods = false)
public class MentoringMessages {

    @Bean
    InAppEntryShape mentoringProposalEntry() {

        return new InAppEntryShape(MessageType.MENTORING_PROPOSAL, MentoringNoticesAdapter.LINE_FACT,
                MentoringNoticesAdapter.LINK_FACT, true);
    }

    @Bean
    InAppEntryShape mentoringDeclinedEntry() {

        return new InAppEntryShape(MessageType.MENTORING_DECLINED, MentoringNoticesAdapter.LINE_FACT,
                MentoringNoticesAdapter.LINK_FACT, true);
    }

    @Bean
    InAppEntryShape mentoringEndedEntry() {

        return new InAppEntryShape(MessageType.MENTORING_ENDED, MentoringNoticesAdapter.LINE_FACT,
                MentoringNoticesAdapter.LINK_FACT, true);
    }

    @Bean
    InAppEntryShape mentoringWordsAskedEntry() {

        return new InAppEntryShape(MessageType.MENTORING_WORDS_ASKED, MentoringNoticesAdapter.LINE_FACT,
                MentoringNoticesAdapter.LINK_FACT, true);
    }

    @Bean
    InAppEntryShape mentoringMeetingSetEntry() {

        return new InAppEntryShape(MessageType.MENTORING_MEETING_SET, MentoringNoticesAdapter.LINE_FACT,
                MentoringNoticesAdapter.LINK_FACT, true);
    }

    @Bean
    InAppEntryShape mentoringMeetingReminderEntry() {

        return new InAppEntryShape(MessageType.MENTORING_MEETING_REMINDER, MentoringNoticesAdapter.LINE_FACT,
                MentoringNoticesAdapter.LINK_FACT, true);
    }

    @Bean
    InAppEntryShape mentoringProposalReopenedEntry() {

        return new InAppEntryShape(MessageType.MENTORING_PROPOSAL_REOPENED, MentoringNoticesAdapter.LINE_FACT,
                MentoringNoticesAdapter.LINK_FACT, true);
    }

    @Bean
    InAppEntryShape mentoringWordsReceivedEntry() {

        return new InAppEntryShape(MessageType.MENTORING_WORDS_RECEIVED, MentoringNoticesAdapter.LINE_FACT,
                MentoringNoticesAdapter.LINK_FACT, true);
    }

    @Bean
    InAppEntryShape mentoringListingAskEntry() {

        return new InAppEntryShape(MessageType.MENTORING_LISTING_ASK, MentoringNoticesAdapter.LINE_FACT,
                MentoringNoticesAdapter.LINK_FACT, true);
    }

    @Bean
    InAppEntryShape mentoringListedEntry() {

        return new InAppEntryShape(MessageType.MENTORING_LISTED, MentoringNoticesAdapter.LINE_FACT,
                MentoringNoticesAdapter.LINK_FACT, true);
    }
}
