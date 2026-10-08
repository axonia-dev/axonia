package dev.axonia.privacy;

/**
 * One reason {@link AccountErasure#erase(String)} cannot proceed yet, as data rather than a formatted message, so a
 * caller such as the erasure-preflight endpoint can render every blocker - and link to the community that needs to go
 * first - without parsing prose out of an exception message.
 */
public record ErasureBlocker(BlockerKind kind, String organizationId, String communityName) {

    /**
     * {@code ENTERPRISE_MEMBERSHIP}: the person belongs to an enterprise Organization, where their employer is the
     * controller and their data leaves with that Organization, not through self-serve erasure.
     * <p>
     * {@code SOLE_OWNER}: the person is the sole Owner of a hub - a lone {@code role_binding} to the seeded "Owner"
     * role with {@code subject_type = USER}. Hand-over of a hub to another owner is not built, so they must delete the
     * named community first.
     */
    public enum BlockerKind {
        ENTERPRISE_MEMBERSHIP, SOLE_OWNER
    }
}
