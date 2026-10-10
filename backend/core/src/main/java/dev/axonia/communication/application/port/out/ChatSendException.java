package dev.axonia.communication.application.port.out;

import dev.axonia.communication.api.ChatFailureKind;
import dev.axonia.communication.api.ChatSendFailedException;

/**
 * A post that did not arrive, carrying which of the two failure shapes it was. Extends the public
 * {@link ChatSendFailedException} rather than wrapping it: every throw site here already lives beside the transport
 * that knows the real failure, and {@code ChatChannelProbeAdapter} is the one caller outside that transport's own catch
 * blocks, so letting this type itself be the cross-module one means there is nothing to translate.
 */
public class ChatSendException extends ChatSendFailedException {

    public ChatSendException(ChatFailureKind kind, String message) {

        super(kind, message);
    }
}
