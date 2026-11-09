package dev.axonia.events.api;

import java.util.Collection;

/** Never stored: a topic is an OFFER exactly when somebody will present it. */
public enum TopicKind {
    REQUEST, OFFER;

    public static TopicKind forSpeakers(Collection<?> speakers) {

        return speakers.isEmpty() ? REQUEST : OFFER;
    }
}
