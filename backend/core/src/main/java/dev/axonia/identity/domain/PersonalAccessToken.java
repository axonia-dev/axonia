package dev.axonia.identity.domain;

import java.time.Instant;

import lombok.Builder;

/** A member's personal access token, held by its digest only. {@code scopes} is the comma-joined scope list. */
@Builder(toBuilder = true)
public record PersonalAccessToken(Long id, String organizationId, String userId, String tokenHash, String name,
        String scopes, Instant lastUsedAt, Instant revokedAt, Instant expiresAt, Instant createdAt, Instant updatedAt,
        Long version) {

    @Override
    public String toString() {
        return "PersonalAccessToken(id=" + id + ", organizationId=" + organizationId + ", userId=" + userId + ", name="
                + name + ", scopes=" + scopes + ", lastUsedAt=" + lastUsedAt + ", revokedAt=" + revokedAt
                + ", expiresAt=" + expiresAt + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + ", version="
                + version + ")";
    }
}
