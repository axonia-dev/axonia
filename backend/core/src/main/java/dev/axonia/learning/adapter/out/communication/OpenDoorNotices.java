package dev.axonia.learning.adapter.out.communication;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.communication.api.CommunicationPort;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.OutboundMessage;
import dev.axonia.learning.application.port.out.LearnerNotices;
import dev.axonia.learning.domain.OpenDoorNotice;
import dev.axonia.shared.api.web.PublicUrls;
import lombok.RequiredArgsConstructor;

/**
 * One in-app message per community, area and declarer, so the outbox's once-per-subject and once-per-recipient keys are
 * what keep a removed and redeclared area from telling anybody twice. It is never mailed, so its template is a name no
 * mail is rendered from.
 */
@Component
@RequiredArgsConstructor
public class OpenDoorNotices implements LearnerNotices {

    public static final MessageType TYPE = MessageType.of("open_to_questions");

    static final String TEMPLATE = "open-to-questions";

    private final CommunicationPort communication;
    private final PublicUrls publicUrl;

    @Override
    public void notify(OpenDoorNotice notice) {

        communication.enqueue(OutboundMessage.builder().type(TYPE)
                .subjectRef(notice.communityId() + ":" + notice.area() + ":" + notice.declarerId())
                .recipientUserIds(notice.learnerIds()).template(TEMPLATE).inAppOnly(true)
                .facts(Map.of("sessionTitle", notice.declarerName() + " is now open to questions on " + notice.area(),
                        "communityName", notice.communityName(), "searchUrl", searchUrl(notice), "aboutUserId",
                        notice.declarerId()))
                .build());
    }

    private String searchUrl(OpenDoorNotice notice) {

        return publicUrl.publicBaseUrl() + "/communities/" + notice.communitySlug() + "/expertise?term="
                + URLEncoder.encode(notice.area(), StandardCharsets.UTF_8) + "&who=open";
    }
}
