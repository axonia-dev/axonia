package dev.axonia.communication.application.port.out;

import java.net.URI;
import java.util.Map;

import dev.axonia.communication.api.MessageType;

/** Posts one message, rendered from its frozen facts, to a chat webhook. */
public interface ChatSender {

    /** Throws {@link ChatSendException} when the post did not arrive, carrying which failure shape it was. */
    void post(URI url, MessageType type, Map<String, Object> facts);
}
