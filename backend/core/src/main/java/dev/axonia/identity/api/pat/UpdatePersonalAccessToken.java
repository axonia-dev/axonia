package dev.axonia.identity.api.pat;

import java.util.Set;

/**
 * Parameters to change an existing token in place. The secret is never touched: the same plaintext keeps authenticating
 * and simply carries the new scope set on its next call.
 */
public record UpdatePersonalAccessToken(String userId, String organizationId, Long tokenId, String name,
        Set<String> scopes) {
}
