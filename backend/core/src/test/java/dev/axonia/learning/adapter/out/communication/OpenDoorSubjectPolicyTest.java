package dev.axonia.learning.adapter.out.communication;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.communication.api.CommunicationPort;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.OutboundMessage;
import dev.axonia.learning.application.port.out.CommunityAccess;
import dev.axonia.learning.application.port.out.OpenPeople;
import dev.axonia.learning.domain.OpenDoorNotice;
import dev.axonia.learning.domain.OpenDoorSubject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenDoorSubjectPolicyTest {

    private static final String ORG = "org-1";

    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private OpenPeople openPeople;
    @Mock
    private CommunicationPort communication;

    @Test
    void handlesTheOpenToQuestionsTypeAlone() {

        assertThat(new OpenDoorSubjectPolicy(communityAccess, openPeople).handles())
                .containsExactly(MessageType.of("open_to_questions"));
    }

    @Test
    void isEnabledOnlyWhileTheDeclarerIsStillOpenOnTheArea() {

        when(openPeople.isOpen(ORG, 7L, "kafka", "lea")).thenReturn(true);
        when(openPeople.isOpen(ORG, 7L, "kafka", "ben")).thenReturn(false);
        var sut = new OpenDoorSubjectPolicy(communityAccess, openPeople);

        assertThat(sut.isEnabledFor(ORG, OpenDoorSubject.MESSAGE_TYPE, "7:kafka:lea")).isTrue();
        assertThat(sut.isEnabledFor(ORG, OpenDoorSubject.MESSAGE_TYPE, "7:kafka:ben")).isFalse();
    }

    @Test
    void isStillAMemberOnlyForACurrentMemberOfTheCommunityInTheSubject() {

        when(communityAccess.isMember(ORG, 7L, "bob")).thenReturn(true);
        when(communityAccess.isMember(ORG, 7L, "carol")).thenReturn(false);
        var sut = new OpenDoorSubjectPolicy(communityAccess, openPeople);

        assertThat(sut.isStillAMember(ORG, "7:kafka:lea", "bob")).isTrue();
        assertThat(sut.isStillAMember(ORG, "7:kafka:lea", "carol")).isFalse();
    }

    @Test
    void theNoticeIsOneInAppMessagePerCommunityAreaAndDeclarer() {

        var sut = new OpenDoorNotices(communication, () -> "https://axonia.example");

        sut.notify(OpenDoorNotice.builder().communityId(7L).communitySlug("platform").communityName("Platform Guild")
                .declarerId("lea").declarerName("Lea Moser").area("c++").learnerIds(List.of("bob", "carol")).build());

        var sent = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(communication).enqueue(sent.capture());
        assertThat(sent.getValue()).isEqualTo(OutboundMessage.builder().type(OpenDoorSubject.MESSAGE_TYPE)
                .subjectRef("7:c++:lea").recipientUserIds(List.of("bob", "carol")).template("open-to-questions")
                .inAppOnly(true).aboutUserId("lea")
                .facts(Map.of("sender", Map.of("name", "Platform Guild"), "sessionTitle",
                        "Lea Moser is open to questions", "line", "c++", "searchUrl",
                        "https://axonia.example/communities/platform/expertise?term=c%2B%2B&who=open"))
                .build());
    }
}
