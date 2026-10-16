package dev.axonia.events.application.port.out;

import java.util.Collection;
import java.util.Map;

/** How many loves each presented slot received; a slot nobody loved is absent from the answer. */
public interface TalkLoveCounts {

    Map<Long, Long> lovesBySlot(String organizationId, Collection<Long> slotIds);
}
