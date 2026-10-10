package dev.axonia.identity.domain;

import java.time.Instant;

import lombok.Builder;

/** One secret of an agent connection - a code, an access or a refresh token - held by its digest only. */
@Builder(toBuilder = true)
public record AgentConnectionToken(Long id, String organizationId, Long connectionId, AgentConnectionTokenKind kind,
        String tokenHash, Instant expiresAt, Instant consumedAt, Instant createdAt, Instant updatedAt, Long version) {

    public boolean isLiveAt(Instant now) {
        return expiresAt.isAfter(now);
    }

    @Override
    public String toString() {
        return "AgentConnectionToken(id=" + id + ", organizationId=" + organizationId + ", connectionId=" + connectionId
                + ", kind=" + kind + ", expiresAt=" + expiresAt + ", consumedAt=" + consumedAt + ", createdAt="
                + createdAt + ", updatedAt=" + updatedAt + ", version=" + version + ")";
    }
}
