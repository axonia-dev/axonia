package dev.axonia.events.api;

import java.util.Collection;
import java.util.Map;

/**
 * The titles of the slots and topics other modules reference as activity sources; an id that no longer resolves is
 * absent.
 */
public interface ActivityTitles {

    Map<Long, String> slotTitles(Collection<Long> ids);

    Map<Long, String> topicTitles(Collection<Long> ids);
}
