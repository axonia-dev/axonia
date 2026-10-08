package dev.axonia.communication.internal;

import dev.axonia.communication.ChatFailureKind;
import dev.axonia.communication.ChatSendFailedException;

/**
 * A post that did not arrive, carrying which of the two failure shapes it was. Extends the public
 * {@link ChatSendFailedException} rather than wrapping it: every throw site here already lives beside the transport
 * that knows the real failure, and {@link ChatChannelProbeAdapter} is the one caller outside that transport's own catch
 * blocks, so letting this type itself be the cross-module one means there is nothing to translate.
 */
class ChatSendException extends ChatSendFailedException {

    ChatSendException(ChatFailureKind kind, String message) {

        super(kind, message);
    }
}
