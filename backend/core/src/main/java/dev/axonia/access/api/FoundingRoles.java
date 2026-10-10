package dev.axonia.access.api;

import dev.axonia.access.api.spi.PermissionCatalogue;

/**
 * The two system roles an Organization is founded with, as described to its administrators, and who holds Owner. The
 * Owner is given every permission that applies in the Organization (see {@link PermissionCatalogue}); the Member role
 * starts with none.
 */
public record FoundingRoles(OwnerBinding owner, String ownerDescription, String memberDescription) {

    public static FoundingRoles ofAnOrganization(OwnerBinding owner) {

        return new FoundingRoles(owner, "Full control of the organization, including deletion and ownership transfer.",
                "Baseline membership: browse and participate in communities");
    }
}
