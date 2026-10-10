package dev.axonia.identity.application.scim;

import java.time.Instant;

/**
 * Admin-facing SCIM token metadata. Never carries the hash/secret. {@code status} is derived ("ACTIVE" | "REVOKED" |
 * "EXPIRED") from the token's lifecycle timestamps versus the clock.
 */
public record ScimTokenView(Long id, String label, Instant lastUsedAt, Instant expiresAt, Instant createdAt,
        String status) {
}
