package dev.axonia.events.api;

import java.util.Collection;
import java.util.Map;

/**
 * The titles of the sessions, slots and topics other modules reference; an id that no longer resolves is absent.
 */
public interface ActivityTitles {

    Map<Long, String> slotTitles(Collection<Long> ids);

    Map<Long, String> topicTitles(Collection<Long> ids);

    /**
     * Each session under the name it is shown by everywhere (its own title, its first titled talk, its series), with
     * its community.
     */
    Map<Long, NamedSession> sessionNames(Collection<Long> ids);
}
