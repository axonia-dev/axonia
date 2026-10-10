package dev.axonia.identity.api.spi;

import java.util.List;

/**
 * Read-only port over the identity provider's record of which Organizations a subject belongs to.
 *
 * <p>
 * This is an <strong>admin-path read</strong> used to power an Org switcher: it enumerates the Organizations a single
 * identity ({@code sub}) is a member of, independently of the active {@code org} claim. It is <strong>never</strong> on
 * the RLS write path and must not resolve the current Organization.
 */
public interface OrganizationMembershipSource {

    /**
     * Organizations the given subject (sub) is a member of, read live from the identity provider. Admin-path read -
     * never on the RLS write path. Empty list if none.
     */
    List<OrganizationMembership> membershipsFor(String subject);

    /** id = the axonia organization id (the value carried by the signed {@code org} claim). */
    record OrganizationMembership(String id, String displayName) {
    }
}
