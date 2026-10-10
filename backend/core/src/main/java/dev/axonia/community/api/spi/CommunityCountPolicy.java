package dev.axonia.community.api.spi;

import java.util.Optional;

/**
 * Whether the given Organization's community count is fixed at its founding: every community it will ever have was
 * created with it, so none can be started or archived afterwards.
 *
 * <p>
 * The Enterprise answer, no, is the default: an enterprise tenant starts and archives communities freely. A contributor
 * answers only for the Organizations it governs and returns empty for any other; at most one contributor may answer for
 * an Organization.
 */
public interface CommunityCountPolicy {

    Optional<Boolean> countFixedAtFounding(String organizationId);
}
