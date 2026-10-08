package dev.axonia.access;

import java.util.Optional;
import java.util.Set;

/**
 * Which permissions apply in the given Organization: those an Owner is seeded with and a role may carry there.
 *
 * <p>
 * The Enterprise answer, every permission the edition knows ({@link KnownPermissions}), is the default. A contributor
 * answers only for the Organizations it governs and returns empty for any other; at most one contributor may answer for
 * an Organization.
 */
public interface PermissionCatalogue {

    Optional<Set<Permission>> applicableTo(String organizationId);
}
