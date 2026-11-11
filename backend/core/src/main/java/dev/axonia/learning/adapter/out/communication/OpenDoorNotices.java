package dev.axonia.learning.adapter.out.communication;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.communication.api.CommunicationPort;
import dev.axonia.communication.api.OutboundMessage;
import dev.axonia.learning.application.port.out.LearnerNotices;
import dev.axonia.learning.domain.OpenDoorNotice;
import dev.axonia.learning.domain.OpenDoorSubject;
import dev.axonia.shared.api.web.PublicUrls;
import lombok.RequiredArgsConstructor;

/**
 * One in-app message per community, area and declarer, so the outbox's once-per-subject and once-per-recipient keys are
 * what keep a removed and redeclared area from telling anybody twice. The headline names the declarer alone, the line
 * carries the area so a narrow panel never cuts it away, and the community is the sender, as on every other community
 * message. It is never mailed, so its template is a name no mail is rendered from.
 */
@Component
@RequiredArgsConstructor
public class OpenDoorNotices implements LearnerNotices {

    static final String TEMPLATE = "open-to-questions";

    static final String SENDER_FACT = "sender";

    static final String HEADLINE_FACT = "sessionTitle";

    public static final String LINE_FACT = "line";

    public static final String LINK_FACT = "searchUrl";

    private final CommunicationPort communication;
    private final PublicUrls publicUrl;

    @Override
    public void notify(OpenDoorNotice notice) {

        communication.enqueue(OutboundMessage.builder().type(OpenDoorSubject.MESSAGE_TYPE)
                .subjectRef(new OpenDoorSubject(notice.communityId(), notice.area(), notice.declarerId()).ref())
                .recipientUserIds(notice.learnerIds()).template(TEMPLATE).inAppOnly(true)
                .aboutUserId(notice.declarerId())
                .facts(Map.of(SENDER_FACT, Map.of("name", notice.communityName()), HEADLINE_FACT,
                        notice.declarerName() + " is open to questions", LINE_FACT, notice.area(), LINK_FACT,
                        searchUrl(notice)))
                .build());
    }

    private String searchUrl(OpenDoorNotice notice) {

        return publicUrl.publicBaseUrl() + "/communities/" + notice.communitySlug() + "/expertise?term="
                + URLEncoder.encode(notice.area(), StandardCharsets.UTF_8) + "&who=open";
    }
}
