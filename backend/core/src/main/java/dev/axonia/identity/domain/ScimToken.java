package dev.axonia.identity.domain;

import java.time.Instant;

import lombok.Builder;

/** A SCIM bearer credential, held by its digest only. */
@Builder(toBuilder = true)
public record ScimToken(Long id, String organizationId, String tokenHash, String label, Instant lastUsedAt,
        Instant revokedAt, Instant expiresAt, Instant createdAt, Instant updatedAt, Long version) {

    @Override
    public String toString() {
        return "ScimToken(id=" + id + ", organizationId=" + organizationId + ", label=" + label + ", lastUsedAt="
                + lastUsedAt + ", revokedAt=" + revokedAt + ", expiresAt=" + expiresAt + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + ", version=" + version + ")";
    }
}
