package dev.axonia.identity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the org-unit a person sits in, sourced from the synced directory (SCIM enterprise extension). Empty/absent for
 * self-serve organizations and for users with no unit data. The finest grain of {@link OrgUnit} is the "team".
 */
public interface OrgUnitDirectory {

    /** The org-unit a person sits in, or empty when no unit data is synced. */
    Optional<OrgUnit> teamOf(String organizationId, String userId);

    /** Bulk lookup for set operations (cross-team reach); the map contains only users that have a unit. */
    Map<String, OrgUnit> teamsOf(String organizationId, Collection<String> userIds);
}
