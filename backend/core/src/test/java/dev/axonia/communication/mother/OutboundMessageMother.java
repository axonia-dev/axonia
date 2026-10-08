package dev.axonia.communication.mother;

import java.util.List;
import java.util.Map;

import dev.axonia.communication.MessageType;
import dev.axonia.communication.OutboundMessage;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OutboundMessageMother {

    public OutboundMessage.OutboundMessageBuilder any() {

        return OutboundMessage.builder().type(MessageType.SESSION_WRAP_UP).subjectRef("42")
                .recipientUserIds(List.of("user-1")).template("session-wrap-up")
                .facts(Map.of("sessionTitle", "Frontend Dojo"));
    }
}
