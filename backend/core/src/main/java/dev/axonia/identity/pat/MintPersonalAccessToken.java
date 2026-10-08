package dev.axonia.identity.pat;

import java.time.Instant;
import java.util.Set;

/**
 * Parameters to mint a token. The port itself permits a null {@code expiresAt} (never expires) and the stored token
 * honours one; its only caller, {@code PersonalTokenController}, requires a present, future value at the HTTP boundary,
 * so a null reaching here in practice means a new caller has been added without that requirement.
 */
public record MintPersonalAccessToken(String userId, String organizationId, String name, Set<String> scopes,
        Instant expiresAt) {
}
