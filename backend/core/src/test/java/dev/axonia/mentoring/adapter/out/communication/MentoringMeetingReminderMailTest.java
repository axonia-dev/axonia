package dev.axonia.mentoring.adapter.out.communication;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.axonia.communication.api.CommunicationPort;
import dev.axonia.communication.api.OutboundMessage;
import dev.axonia.communication.application.MailRenderer;
import dev.axonia.communication.application.RenderedMail;
import dev.axonia.communication.configuration.MailTemplateConfig;
import dev.axonia.mentoring.application.port.out.MentoringNotices.MeetingReminder;
import dev.axonia.mentoring.domain.MentoringGuild;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.PlannedMeeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The reminder mail rendered from the very facts the notice sends: the partner, the day and the link, nothing else. */
class MentoringMeetingReminderMailTest {

    private RenderedMail render() {

        var communication = mock(CommunicationPort.class);
        new MentoringNoticesAdapter(communication, () -> "https://axonia.example").meetingReminder(MeetingReminder
                .builder().organizationId("acme")
                .pairing(Pairing.builder().id(90L).communityId(7L).mentorUserId("mia").menteeUserId("noah")
                        .startedAt(Instant.parse("2026-11-01T09:00:00Z")).build())
                .meeting(PlannedMeeting.builder().id(12L).pairingId(90L).on(LocalDate.parse("2026-11-19"))
                        .topicIds(List.of()).plannedBy("noah").plannedAt(Instant.parse("2026-11-16T10:00:00Z")).build())
                .guild(new MentoringGuild("platform", "Platform")).partnerName("Noah").recipientId("mia").build());
        var message = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(communication).enqueue(message.capture());
        var facts = new HashMap<>(message.getValue().facts());
        facts.put("unsubscribePageUrl", "https://axonia.example/unsubscribe/t");
        return new MailRenderer(new MailTemplateConfig().mailTemplateEngine()).render(message.getValue().template(),
                facts);
    }

    @Test
    void theSubjectNamesThePartnerAndTomorrow() {

        // when
        var actual = render();

        // then
        assertThat(actual.subject()).isEqualTo("Mentoring tomorrow with Noah");
    }

    @Test
    void theHtmlNamesThePartnerAndTheDayAndLinksToTheJournal() {

        // when
        var actual = render();

        // then
        assertThat(actual.html()).contains("Mentoring tomorrow with Noah")
                .contains("The next meeting with Noah is tomorrow, Thursday 19 November.").contains("Open the journal")
                .contains("href=\"https://axonia.example/communities/platform/me/mentoring/90?org=acme\"")
                .contains("Mentoring through Platform.").contains("https://axonia.example/unsubscribe/t");
    }

    @Test
    void theTextCarriesTheSameFacts() {

        // when
        var actual = render();

        // then
        assertThat(actual.text()).isEqualTo("""
                Mentoring tomorrow with Noah

                The next meeting with Noah is tomorrow, Thursday 19 November.

                Open the journal: https://axonia.example/communities/platform/me/mentoring/90?org=acme

                Mentoring through Platform.

                Unsubscribe: https://axonia.example/unsubscribe/t""");
    }
}
