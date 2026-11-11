package dev.axonia.learning.domain;

import dev.axonia.communication.api.MessageType;

/**
 * What an open-to-questions notice is about: one declarer's openness on one area in one community, written
 * {@code <communityId>:<area>:<declarerId>}. An area name never holds a colon, so the declarer is whatever follows the
 * second one.
 */
public record OpenDoorSubject(long communityId, String area, String declarerId) {

    /** The one message type sent about such a subject. */
    public static final MessageType MESSAGE_TYPE = MessageType.OPEN_TO_QUESTIONS;

    private static final char SEPARATOR = ':';

    public static OpenDoorSubject parse(String ref) {

        var afterCommunity = ref.indexOf(SEPARATOR);
        var afterArea = ref.indexOf(SEPARATOR, afterCommunity + 1);
        return new OpenDoorSubject(Long.parseLong(ref.substring(0, afterCommunity)),
                ref.substring(afterCommunity + 1, afterArea), ref.substring(afterArea + 1));
    }

    /** How every subject about the declarer ends, so their erasure can find them all. */
    public static String aboutSuffix(String declarerId) {

        return SEPARATOR + declarerId;
    }

    public String ref() {

        return communityId + String.valueOf(SEPARATOR) + area + aboutSuffix(declarerId);
    }
}
