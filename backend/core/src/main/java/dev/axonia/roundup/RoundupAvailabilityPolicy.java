package dev.axonia.roundup;

import java.util.Optional;

/**
 * Whether the quarterly roundup is available to the given Organization: whether it has a provisioned directory a
 * roundup could be addressed to.
 *
 * <p>
 * The Enterprise answer, yes, is the default. A contributor answers only for the Organizations it governs and returns
 * empty for any other; at most one contributor may answer for an Organization.
 */
public interface RoundupAvailabilityPolicy {

    Optional<Boolean> available(String organizationId);
}
