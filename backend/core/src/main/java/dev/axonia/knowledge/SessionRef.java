package dev.axonia.knowledge;

/**
 * A session reference as knowledge needs it: identity, the community that owns it, and whether it has already been held
 * - the floor between the schedule-management gate and open membership for adding a resource.
 */
public record SessionRef(Long id, Long communityId, boolean held) {
}
