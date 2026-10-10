package dev.axonia.organization.application.port.out;

import java.time.Instant;
import java.util.Optional;

import dev.axonia.organization.api.OrganizationRegistration;
import dev.axonia.organization.domain.RegistryEntry;

/** Where the Organization registry keeps its rows. */
public interface OrganizationStore {

    /** Inserts a fresh registry row: active, communications on. */
    RegistryEntry register(OrganizationRegistration registration);

    boolean exists(String id);

    Optional<RegistryEntry> find(String id);

    /** Writes back a row that already exists. */
    RegistryEntry update(RegistryEntry entry);

    /**
     * Flips the organization-wide kill switch for outbound member messaging, stamping {@code updatedAt}. Returns the
     * affected row count, {@code 0} for an unknown id.
     */
    int setCommunicationsEnabled(String id, boolean enabled, Instant updatedAt);

    void delete(String id);
}
