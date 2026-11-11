package dev.axonia.access.api.spi;

import java.util.Optional;

/**
 * Whether the given Organization's ownership is anchored outside the product: restored by its operator whatever role
 * bindings are removed inside it, so no revocation can leave the Organization without an Owner.
 *
 * <p>
 * The Enterprise answer, no, is the default: there, an Organization with no direct Owner cannot be repaired from inside
 * the product. A contributor answers only for the Organizations it governs and returns empty for any other; at most one
 * contributor may answer for an Organization.
 */
public interface OwnershipAnchorPolicy {

    Optional<Boolean> anchoredOutsideTheProduct(String organizationId);
}
