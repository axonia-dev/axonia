package dev.axonia.identity.api.connection;

import java.time.Instant;
import java.util.Set;

/** A no-secret view of a connection for the owner's management screen. {@code status} is ACTIVE | REVOKED | ENDED. */
public record ConnectionSummary(Long id, String clientName, String clientHost, Set<String> scopes, Instant activatedAt,
        Instant lastUsedAt, Instant endsAt, String status) {
}
