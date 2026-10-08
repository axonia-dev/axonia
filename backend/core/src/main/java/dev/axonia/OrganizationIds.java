package dev.axonia;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The narrow privileged reads of the {@code organization} table, run through {@link TenantDiscoveryPool}'s tiny second
 * pool. {@code axonia_app} carries no {@code app.current_org} GUC where these are called - a scheduled tick has no
 * organization yet to scope to, discovering them is the point - and Row-Level Security fails closed for it, so it would
 * see zero rows everywhere rather than every row.
 *
 * <p>
 * <strong>Every method here is privileged and unscoped.</strong> Each takes what it is given and reads across
 * organizations, so no caller may pass an id that came from a request body, a path variable or a query parameter. A
 * request-path caller passes the organization id the caller's own JWT was resolved to, which the caller cannot choose.
 *
 * <p>
 * {@link #all()} and {@link #ofKind(String)} are step 1 of every scheduled tick, returning the ids of {@code ACTIVE}
 * organizations and nothing else. Their {@code lifecycle_state = 'ACTIVE'} filter is not optional. {@link #all()}'s
 * {@code ORDER BY id} exists so a caller that rotates the list tick to tick - a sweep's drain - has a stable sequence
 * to rotate through; an unordered scan gives no such guarantee, which is what let a tenant late in whatever order
 * Postgres happened to return starve. {@code id} is already in the column list this method reads, so ordering by it
 * needs no new grant for the tenant-discovery role. {@code OrganizationLifecycleFilter} refuses every HTTP request for
 * a {@code SUSPENDED} or {@code CLOSED} organization, and background work is the one surface that filter cannot reach.
 * For the communication sweep it stops mail to a tenant that would be refused on click-through; for any other sweep it
 * stops work for an organization after it has been switched off.
 *
 * <p>
 * Lives in the root package beside {@link OrganizationScope} because it is the shared primitive every scheduled job
 * uses, and a second copy would duplicate the one cross-tenant read kept deliberately singular.
 */
@Repository
public class OrganizationIds {

    private final JdbcClient jdbc;

    OrganizationIds(@Qualifier("tenantDiscoveryJdbcClient") JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> all() {

        return jdbc.sql("SELECT id FROM organization WHERE lifecycle_state = 'ACTIVE' ORDER BY id").query(String.class)
                .list();
    }

    /**
     * The active organizations of one kind, for a caller that must act on one kind explicitly rather than infer it from
     * whether some other row exists.
     */
    public List<String> ofKind(String kind) {

        return jdbc.sql("SELECT id FROM organization WHERE lifecycle_state = 'ACTIVE' AND kind = :kind")
                .param("kind", kind).query(String.class).list();
    }

    /**
     * The raw {@code kind} column of one organization, {@code "HUB"} for a self-serve hub or {@code "ENTERPRISE"} for
     * an enterprise tenant, crossed as a {@code String} rather than {@code OrganizationKind} so a caller does not
     * recompile against that enum. Empty when the organization does not exist.
     *
     * <p>
     * Unlike the two above, this one may run on the request path. It is still the privileged, RLS-bypassing,
     * two-connection discovery pool, so it must only ever be handed the organization id the request's own credential
     * resolved to - never one the caller supplied.
     *
     * <p>
     * It deliberately does not filter {@code lifecycle_state}. Its callers are HTTP requests, which
     * {@code OrganizationLifecycleFilter} has already refused for a {@code SUSPENDED} or {@code CLOSED} organization
     * before any controller runs, so the filter the sweeps need here would only be able to turn an answer the caller is
     * entitled to into a 404. A caller that is not an HTTP request must apply the check itself.
     */
    public Optional<String> kindOf(String organizationId) {

        return jdbc.sql("SELECT kind FROM organization WHERE id = :id").param("id", organizationId).query(String.class)
                .optional();
    }
}
