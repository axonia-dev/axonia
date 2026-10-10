package dev.axonia.organization.api;

import java.util.Optional;

/**
 * Read port for the Organization registry. After collapsing the two-level organization→organization model, each
 * organization IS exactly one Organization; its {@code id} equals the organization identifier.
 */
public interface Organizations {

    /** Returns the Organization for the current JWT organization. Creates the row on first access if absent. */
    Organization current();

    /** Looks up an Organization by its id (= organization id). Returns empty when not found. */
    Optional<Organization> byId(String id);
}
