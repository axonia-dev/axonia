package dev.axonia.events.api;

import java.util.Optional;

/** Which session a slot belongs to. */
public interface SlotSessions {

    Optional<Long> sessionIdOf(Long slotId);
}
