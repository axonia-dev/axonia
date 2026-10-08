package dev.axonia;

import java.util.Optional;

/**
 * Shared-kernel seam: the person whose rows the current unit of work may touch, or empty when unauthenticated.
 *
 * <p>
 * The sibling of {@link OrganizationResolver}, and the second isolation axis in the product. A person may belong to no
 * Organization at all, so a person-rooted row cannot be scoped by one.
 */
@FunctionalInterface
public interface SubjectResolver {

    Optional<String> currentSubject();
}
