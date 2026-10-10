package dev.axonia.access.api;

/** Who owns an Organization, as the modules acting on its behalf need to know it. */
public interface OrganizationOwners {

    /**
     * Whether {@code subject} is the sole Owner of this Organization. Ungated: the caller acts across an arbitrary
     * Organization with no HTTP request and no permission of its own.
     */
    boolean isSoleOwner(String organizationId, String subject);

    /** Whether the caller holds this Organization's seeded Owner role through any binding the gate matches. */
    boolean callerHoldsOwnerRole(String organizationId);
}
