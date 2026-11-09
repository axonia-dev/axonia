package dev.axonia.events.application.port.out;

import java.util.Collection;
import java.util.Set;

/** Tells which of these user ids are people still in the organization: neither erased nor deprovisioned. */
public interface PresentPeople {

    Set<String> presentAmong(String organizationId, Collection<String> userIds);
}
