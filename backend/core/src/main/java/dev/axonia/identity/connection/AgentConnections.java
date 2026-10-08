package dev.axonia.identity.connection;

import java.util.List;
import java.util.Optional;

public interface AgentConnections {

    /** Records a person's consent to one MCP client and returns the {@code axc_} code plaintext, once. */
    String authorize(AuthorizeConnection command);

    /** Exchanges a code for a token pair; empty means {@code invalid_grant}. */
    Optional<IssuedTokens> redeem(RedeemCode command);

    /** Rotates a refresh token for a fresh pair; empty means {@code invalid_grant}. */
    Optional<IssuedTokens> refresh(RefreshConnection command);

    Optional<ConnectionPrincipal> authenticate(String accessToken);

    /** RFC 7009: revokes the whole connection behind the given token, silent on an unknown token. */
    void revokeToken(String token);

    List<ConnectionSummary> list(String userId, String organizationId);

    /** Revokes one of the caller's own connections by id. Throws NotFoundException if it is not theirs. */
    void revoke(String userId, String organizationId, Long connectionId);

    void recordUsage(String organizationId, Long connectionId);

    /**
     * Deletes expired tokens and long-retired connections for the organization. The caller enters the organization
     * scope.
     */
    int sweep(String organizationId);
}
