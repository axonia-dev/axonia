package dev.axonia.identity.application.scim;

import java.time.Instant;
import java.util.Optional;

public interface ScimTokens {

    /** Authenticates a bearer token; rejects (empty) tokens that are revoked or expired. */
    Optional<ScimPrincipal> authenticate(String bearerToken);

    /**
     * Mints a new labeled SCIM token for the Organization. Additive: existing tokens are left intact. The port itself
     * permits a null {@code expiresAt} (never expires); its only caller, {@link ScimTokenAdminService}, requires a
     * present, future value at the HTTP boundary, so a null reaching here in practice means a new caller has been added
     * without that requirement. Returns plaintext ONCE.
     */
    String generate(String organizationId, String label, Instant expiresAt);

    /** Revokes a single SCIM token by id within the given Organization. */
    void revoke(String organizationId, Long tokenId);

    /**
     * Records that a bearer token was just used, advancing {@code last_used_at}. Must be called AFTER the Organization
     * GUC is established (i.e. after the SecurityContext is set) so the {@code scim_token_org_write} RLS policy matches
     * the caller's row. A failed write is swallowed and never fails the SCIM request.
     */
    void recordUsage(String organizationId, String bearerToken);
}
