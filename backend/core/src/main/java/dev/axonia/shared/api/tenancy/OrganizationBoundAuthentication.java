package dev.axonia.shared.api.tenancy;

/**
 * Shared-kernel marker interface: an {@link org.springframework.security.core.Authentication} token whose principal was
 * resolved to a specific Organization (e.g. a SCIM bearer token). Implementations are placed in their respective
 * bounded contexts; the shared {@link dev.axonia.shared.configuration.PersistenceConfig#organizationResolver()
 * organizationResolver} inspects this interface so {@code shared} stays decoupled from any specific bounded context.
 */
public interface OrganizationBoundAuthentication {

    /** The Organization this authenticated principal belongs to; sets {@code app.current_org} for RLS. */
    String organizationId();
}
