package dev.axonia.identity.pat;

import java.util.List;
import java.util.Optional;

public interface PersonalAccessTokens {

    /**
     * Mints a token for a member. Additive: existing tokens are left intact. Returns the {@code axp_} plaintext ONCE.
     */
    String mint(MintPersonalAccessToken command);

    /**
     * Authenticates a bearer token; returns empty for unknown, revoked, or expired tokens. Does NOT check whether the
     * owning profile is still active — that runs in the agent MCP filter after the Organization GUC is set
     * (identity_user is RLS-scoped and invisible pre-org).
     */
    Optional<PersonalTokenPrincipal> authenticate(String bearerToken);

    /** Lists the caller's own tokens within the Organization, newest first. No secret is ever returned. */
    List<PersonalTokenSummary> list(String userId, String organizationId);

    /** Revokes one of the caller's own tokens by id. Throws NotFoundException if it is not theirs. */
    void revoke(String userId, String organizationId, Long tokenId);

    /**
     * Changes an active token's name and scope set in place, leaving the secret intact. Throws NotFoundException when
     * the token is not the caller's, and ValidationException when it is already revoked or expired.
     */
    void update(UpdatePersonalAccessToken command);

    /**
     * Advances {@code last_used_at}. Must be called AFTER the Organization GUC is set so the org-write RLS policy
     * matches the row. A failed write is swallowed and never fails the request.
     */
    void recordUsage(String organizationId, Long tokenId);
}
