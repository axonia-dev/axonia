package dev.axonia.privacy;

import java.util.Optional;

/**
 * Whether the members of the given Organization exercise their data rights themselves - exporting their own data and
 * erasing their own account from it - rather than through the Organization, whose administrator exports the whole
 * Organization instead.
 *
 * <p>
 * The Enterprise answer, no, is the default: an enterprise membership is governed by the tenant's administrator. A
 * contributor answers only for the Organizations it governs and returns empty for any other; at most one contributor
 * may answer for an Organization.
 */
public interface MemberDataRightsPolicy {

    Optional<Boolean> exercisedByMembers(String organizationId);
}
