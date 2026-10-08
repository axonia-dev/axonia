package dev.axonia.identity;

import java.util.Optional;

/**
 * Port: the single place that answers "which identity provider, and for Entra which customer tenant" for an
 * Organization.
 *
 * <p>
 * An empty result is normal rather than an error. A HUB Organization has no SSO connection at all, and callers are
 * expected to fall back to the default provider.
 */
public interface OrganizationIdpResolver {

    Optional<OrganizationIdp> resolve(String organizationId);
}
