package dev.axonia.health;

import java.util.Collection;
import java.util.Set;

/**
 * Whether contributors are still here: not deprovisioned, not erased. Declared by community-health, implemented in
 * dev.axonia.identity.internal over the published UserDirectory, so health's core never imports an identity type.
 */
public interface PresenceSignals {

    Set<String> presentOf(String organizationId, Collection<String> userIds);
}
