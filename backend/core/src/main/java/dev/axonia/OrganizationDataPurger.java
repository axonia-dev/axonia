package dev.axonia;

/**
 * Deletes every row a bounded context holds for one organization. Each module owning organization-scoped tables
 * implements this inside its {@code internal} package; the hub context injects the full list and invokes it inside the
 * single hub-deletion transaction. Implementations delete only their own module's tables, in that module's FK order,
 * and rely on the caller's RLS scope (the connection's {@code app.current_org}) as the outer isolation guarantee.
 * Ordering across modules is expressed with {@code @Order} where a cross-module FK demands it (events before
 * community).
 */
public interface OrganizationDataPurger {

    void purge(String organizationId);
}
