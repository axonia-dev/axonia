package dev.axonia.access.api;

/**
 * System-initiated access seeding for newly founded Organizations and their joiners, ungated because the founding
 * module has already decided the outcome. Every method must execute inside a transaction whose RLS scope is
 * {@code organizationId}; none of them read the security context.
 */
public interface AccessSeeding {

    /** Seeds a fresh enterprise Organization's role catalogue and binds its Owner. */
    void seedEnterpriseAccess(String organizationId, OwnerBinding owner);

    /** Seeds a fresh Organization's two system roles as {@code roles} describes them, and binds its Owner. */
    void seedAccess(String organizationId, FoundingRoles roles);

    /** Binds {@code group} to the Owner role unless it already is. Returns whether a binding was added. */
    boolean ensureGroupOwner(String organizationId, OwnerBinding.Group group);

    /** Binds a joiner to the Member role unless they already hold a USER binding. Returns whether one was added. */
    boolean bindMemberIfAbsent(String organizationId, String subject, String subjectLabel);

    /** Removes every USER binding the subject holds. Returns whether there was any. */
    boolean unbindUser(String organizationId, String subject);
}
