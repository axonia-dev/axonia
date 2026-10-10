package dev.axonia.communication.api;

import java.util.List;
import java.util.Map;

import lombok.Builder;

/**
 * What a sender hands to {@link CommunicationPort#enqueue(OutboundMessage)}: a message type, who it is about, who
 * should receive it, which template renders it, and the facts already resolved by the sender. The sender owns who cares
 * about this and what it says; it never learns whether a given recipient actually gets it.
 */
@Builder
public record OutboundMessage(MessageType type, String subjectRef, List<String> recipientUserIds, String template,
        Map<String, Object> facts) {
}
