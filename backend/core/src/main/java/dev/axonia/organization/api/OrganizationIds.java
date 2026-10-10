package dev.axonia.organization.api;

import java.util.List;
import java.util.Optional;

/**
 * The narrow privileged reads of the Organization registry, run on the tenant-discovery pool rather than under an
 * organization's RLS scope: a scheduled tick has no organization yet to scope to, discovering them is the point.
 *
 * <p>
 * <strong>Every method here is privileged and unscoped.</strong> Each takes what it is given and reads across
 * organizations, so no caller may pass an id that came from a request body, a path variable or a query parameter. A
 * request-path caller passes the organization id the caller's own JWT was resolved to, which the caller cannot choose.
 *
 * <p>
 * {@link #all()} and {@link #ofKind(String)} are step 1 of every scheduled tick, returning the ids of {@code ACTIVE}
 * organizations and nothing else. {@link #all()} answers in a stable order, so a caller that rotates the list tick to
 * tick - a sweep's drain - has a stable sequence to rotate through. {@code OrganizationLifecycleFilter} refuses every
 * HTTP request for a {@code SUSPENDED} or {@code CLOSED} organization, and background work is the one surface that
 * filter cannot reach.
 */
public interface OrganizationIds {

    /** The ids of every {@code ACTIVE} organization, ordered by id. */
    List<String> all();

    /**
     * The active organizations of one kind, for a caller that must act on one kind explicitly rather than infer it from
     * whether some other row exists.
     */
    List<String> ofKind(String kind);

    /**
     * The raw {@code kind} of one organization, {@code "HUB"} or {@code "ENTERPRISE"}, crossed as a {@code String} so a
     * caller does not recompile against {@link OrganizationKind}. Empty when the organization does not exist. It may
     * run on the request path, but must only ever be handed the organization id the request's own credential resolved
     * to. It deliberately does not filter the lifecycle state: a caller that is not an HTTP request must apply that
     * check itself.
     */
    Optional<String> kindOf(String organizationId);
}
