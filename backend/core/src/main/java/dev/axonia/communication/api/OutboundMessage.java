package dev.axonia.communication.api;

import java.util.List;
import java.util.Map;

import lombok.Builder;

/**
 * What a sender hands to {@link CommunicationPort#enqueue(OutboundMessage)}: a message type, who it is about, who
 * should receive it, which template renders it, and the facts already resolved by the sender. The sender owns who cares
 * about this and what it says; it never learns whether a given recipient actually gets it. An {@code inAppOnly} message
 * reaches the panel alone and is never mailed; left unset, it is mailed too. A message that exists to say something
 * about one member names them in {@code aboutUserId}, which is stored under {@link #ABOUT_USER_FACT} so their erasure
 * removes it for every recipient and their export carries it; left unset, the message is about no one.
 */
@Builder
public record OutboundMessage(MessageType type, String subjectRef, List<String> recipientUserIds, String template,
        Map<String, Object> facts, boolean inAppOnly, String aboutUserId) {

    /** The fact a stored message names the member it is about under. */
    public static final String ABOUT_USER_FACT = "aboutUserId";
}
