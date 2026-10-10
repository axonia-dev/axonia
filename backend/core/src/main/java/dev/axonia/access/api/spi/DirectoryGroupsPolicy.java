package dev.axonia.access.api.spi;

import java.util.Optional;

/**
 * Whether the given Organization has a directory whose groups can be bound to roles: whether a token reaching it can
 * ever carry a {@code groups} claim for a {@code GROUP} binding to match.
 *
 * <p>
 * The Enterprise answer, yes, is the default. A contributor answers only for the Organizations it governs and returns
 * empty for any other; at most one contributor may answer for an Organization.
 */
public interface DirectoryGroupsPolicy {

    Optional<Boolean> groupsApply(String organizationId);
}
