package dev.axonia;

import java.util.Optional;

/**
 * Shared-kernel seam: the Organization whose rows the current unit of work may touch, or empty when unauthenticated.
 */
@FunctionalInterface
public interface OrganizationResolver {

    Optional<String> currentOrganization();
}
