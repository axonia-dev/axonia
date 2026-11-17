package dev.axonia.identity.api;

/**
 * A member who was active is no longer: the directory deactivated them. Published inside the deactivating transaction.
 */
public record MemberDeactivated(String organizationId, String userId) {
}
