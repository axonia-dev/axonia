package dev.axonia.identity.connection;

import java.util.Set;

/**
 * The authenticated result of an {@code axa_} access token: who it acts as, in which Organization, its scopes, and the
 * client it was issued to.
 */
public record ConnectionPrincipal(String userId, String organizationId, Set<String> scopes, Long connectionId,
        String clientId) {
}
