package dev.axonia.organization.api;

import java.util.Optional;

/**
 * The Organization registry as other modules drive it: existence, lookup, naming and the lifecycle. There is
 * deliberately no auto-vivifying "ensure" operation: {@link #register} writes only the registry row, and its caller
 * assembles the rest of the provisioning transaction.
 */
public interface OrganizationRegistry {

    /**
     * Registers an Organization's registry row. Callers must already be executing under an RLS scope for
     * {@code registration.id()} (see {@code dev.axonia.shared.api.tenancy.OrganizationScope}).
     */
    Organization register(OrganizationRegistration registration);

    boolean organizationExists(String id);

    /** Looks up an Organization registry row by id, fully mapped; empty when absent. */
    Optional<Organization> organization(String id);

    /** Renames an Organization, joining the caller's transaction. The row must exist. */
    void rename(String id, String name);

    /** Removes an Organization's registry row, refusing one of another kind than {@code kind}. */
    void delete(String id, OrganizationKind kind);

    /** Moves an Organization from {@code ACTIVE} to {@code SUSPENDED}. */
    void suspend(String id);

    /** Moves an Organization from {@code SUSPENDED} back to {@code ACTIVE}. */
    void unsuspend(String id);

    /** Confirms an Organization is {@code SUSPENDED} or already {@code CLOSED}, and returns it. */
    Organization requireClosable(String id);

    /** Stamps an Organization as {@code CLOSED}, recording when and by whom. */
    Organization markClosed(String id, String operatorSubject);
}
