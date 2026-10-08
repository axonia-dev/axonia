package dev.axonia.organization;

import java.util.Optional;

/**
 * How many members the given Organization has, as its administrator is told.
 *
 * <p>
 * The Enterprise answer is the default: the headcount of the Organization's directory ({@link DirectoryHeadcount}), the
 * same population the admin Users console lists. A contributor answers only for the Organizations whose members it
 * tallies itself and returns empty for any other; at most one contributor may answer for an Organization.
 */
public interface MemberCountPolicy {

    Optional<Long> memberCount(String organizationId);
}
