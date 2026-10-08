package dev.axonia.identity.pat;

import java.util.Set;

/**
 * The authenticated result of a personal access token: who it acts as, in which Organization, and its granted scopes.
 */
public record PersonalTokenPrincipal(String userId, String organizationId, Set<String> scopes, Long tokenId) {
}
