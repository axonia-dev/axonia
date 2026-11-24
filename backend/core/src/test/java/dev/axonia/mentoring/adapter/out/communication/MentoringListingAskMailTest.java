package dev.axonia.mentoring.adapter.out.communication;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.axonia.communication.api.CommunicationPort;
import dev.axonia.communication.api.OutboundMessage;
import dev.axonia.communication.application.MailRenderer;
import dev.axonia.communication.application.RenderedMail;
import dev.axonia.communication.configuration.MailTemplateConfig;
import dev.axonia.mentoring.domain.MentoringGuild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The one-time ask rendered from the very facts the notice sends: the guild, the line and the Mentoring page. */
class MentoringListingAskMailTest {

    private RenderedMail render() {

        var communication = mock(CommunicationPort.class);
        new MentoringNoticesAdapter(communication, () -> "https://axonia.example").listingAsk("acme", 7L,
                new MentoringGuild("platform", "Platform"), "mia");
        var message = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(communication).enqueue(message.capture());
        var facts = new HashMap<>(message.getValue().facts());
        facts.put("unsubscribePageUrl", "https://axonia.example/unsubscribe/t");
        return new MailRenderer(new MailTemplateConfig().mailTemplateEngine()).render(message.getValue().template(),
                facts);
    }

    @Test
    void theSubjectAsksTheQuestion() {

        // when
        var actual = render();

        // then
        assertThat(actual.subject()).isEqualTo("Show among the mentors of Platform?");
    }

    @Test
    void theHtmlSaysOnlyTheMentorShowsAndLinksToTheMentoringPage() {

        // when
        var actual = render();

        // then
        assertThat(actual.html()).contains("Show among the mentors of Platform?")
                .contains("Only the mentor shows, never a mentee.")
                .contains("Show or Keep private, in the Taking mentees door on the Mentoring page.")
                .contains("href=\"https://axonia.example/communities/platform/mentoring?org=acme\"")
                .contains("Mentoring through Platform.").contains("https://axonia.example/unsubscribe/t");
    }

    @Test
    void theTextCarriesTheSameFacts() {

        // when
        var actual = render();

        // then
        assertThat(actual.text()).isEqualTo("""
                Show among the mentors of Platform?

                Only the mentor shows, never a mentee.

                Show or Keep private, in the Taking mentees door on the Mentoring page.

                Open Mentoring: https://axonia.example/communities/platform/mentoring?org=acme

                Mentoring through Platform.

                Unsubscribe: https://axonia.example/unsubscribe/t""");
    }
}
