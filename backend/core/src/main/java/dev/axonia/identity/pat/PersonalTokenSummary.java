package dev.axonia.identity.pat;

import java.time.Instant;
import java.util.Set;

/** A no-secret view of a token for the owner's management screen. {@code status} is ACTIVE | REVOKED | EXPIRED. */
public record PersonalTokenSummary(Long id, String name, Set<String> scopes, Instant lastUsedAt, Instant expiresAt,
        Instant createdAt, String status) {
}
