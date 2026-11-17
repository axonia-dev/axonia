package dev.axonia.mentoring.adapter.out.communication;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.axonia.communication.api.CommunicationPort;
import dev.axonia.communication.api.OutboundMessage;
import dev.axonia.communication.application.MailRenderer;
import dev.axonia.communication.application.RenderedMail;
import dev.axonia.communication.configuration.MailTemplateConfig;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalStatus;
import dev.axonia.mentoring.domain.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The proposal mail rendered from the very facts the notice sends: guild, topics and link, never the message. */
class MentoringProposalMailTest {

    private static final String MESSAGE = "Would love your help with Kafka";

    private RenderedMail render() {

        var communication = mock(CommunicationPort.class);
        new MentoringNoticesAdapter(communication, () -> "https://axonia.example").proposed("acme",
                Proposal.builder().id(40L).communityId(7L).mentorUserId("mia").menteeUserId("noah")
                        .proposedBy(Role.MENTEE).topics(List.of("Kafka", "Giving feedback")).message(MESSAGE)
                        .status(ProposalStatus.OPEN).createdAt(Instant.parse("2026-11-16T10:00:00Z")).build(),
                "Platform", "Noah");
        var message = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(communication).enqueue(message.capture());
        var facts = new HashMap<>(message.getValue().facts());
        facts.put("unsubscribePageUrl", "https://axonia.example/unsubscribe/t");
        return new MailRenderer(new MailTemplateConfig().mailTemplateEngine()).render(message.getValue().template(),
                facts);
    }

    @Test
    void theSubjectNamesTheProposer() {

        // when
        var actual = render();

        // then
        assertThat(actual.subject()).isEqualTo("Noah proposes mentoring");
    }

    @Test
    void theHtmlNamesTheGuildAndTheTopicsAndLinksToMyMentoring() {

        // when
        var actual = render();

        // then
        assertThat(actual.html()).contains("Noah proposes mentoring").contains("Platform").contains("Kafka")
                .contains("Giving feedback").contains("href=\"https://axonia.example/me/mentoring?org=acme\"")
                .contains("Proposed through Platform.").contains("https://axonia.example/unsubscribe/t")
                .doesNotContain(MESSAGE);
    }

    @Test
    void theTextCarriesTheSameFacts() {

        // when
        var actual = render();

        // then
        assertThat(actual.text()).isEqualTo("""
                Noah proposes mentoring

                A proposal to mentor or be mentored in Platform. It waits on My mentoring for 30 days.

                Topics:
                  Kafka
                  Giving feedback

                Open My mentoring: https://axonia.example/me/mentoring?org=acme

                Proposed through Platform.

                Unsubscribe: https://axonia.example/unsubscribe/t""");
    }
}
