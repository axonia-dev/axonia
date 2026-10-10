package dev.axonia.health.api.spi;

import java.util.Collection;
import java.util.Map;

import dev.axonia.health.api.OrgUnitLabels;

/**
 * Read-only org-unit signals for cross-team reach. Declared by community-health, implemented in
 * dev.axonia.identity.adapter.out.health.DirectorySignalsAdapter over the published OrgUnitDirectory. The returned map
 * contains only users that have a synced unit; an empty map means nothing is synced (a self-serve organization), which
 * the core reads as "reach unavailable".
 */
public interface DirectorySignals {

    Map<String, OrgUnitLabels> unitsOf(String organizationId, Collection<String> userIds);
}
