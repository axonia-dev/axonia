package dev.axonia.identity.api.spi;

/**
 * Whether an Organization's {@code groups} claim is filled from another claim of its identity provider, such as Entra's
 * app roles ({@code roles}), and so says nothing about which directory groups the person is in.
 *
 * <p>
 * Then the SCIM-synced directory is the only word on group membership, and a role bound to a directory group reaches
 * its members only through it. An Organization no contributor answers for has a claim that lists its directory groups.
 */
public interface GroupsClaimSource {

    boolean readsAnotherClaim(String organizationId);
}
