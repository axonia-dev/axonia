package dev.axonia.identity.domain;

import java.time.Instant;

import lombok.Builder;

/** An agent's OAuth connection to one member's account, from authorization to revocation or its end. */
@Builder(toBuilder = true)
public record AgentConnection(Long id, String organizationId, String userId, String clientId, String clientName,
        String clientHost, String scopes, String resource, String redirectUri, String codeChallenge,
        Instant activatedAt, Instant lastUsedAt, Instant endsAt, Instant revokedAt, Instant createdAt,
        Instant updatedAt, Long version) {

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isUsableAt(Instant now) {
        return activatedAt != null && !isRevoked() && endsAt.isAfter(now);
    }

    public String statusAt(Instant now) {

        if (isRevoked()) {
            return "REVOKED";
        }
        return endsAt.isAfter(now) ? "ACTIVE" : "ENDED";
    }

    @Override
    public String toString() {
        return "AgentConnection(id=" + id + ", organizationId=" + organizationId + ", userId=" + userId + ", clientId="
                + clientId + ", clientName=" + clientName + ", clientHost=" + clientHost + ", scopes=" + scopes
                + ", resource=" + resource + ", redirectUri=" + redirectUri + ", activatedAt=" + activatedAt
                + ", lastUsedAt=" + lastUsedAt + ", endsAt=" + endsAt + ", revokedAt=" + revokedAt + ", createdAt="
                + createdAt + ", updatedAt=" + updatedAt + ", version=" + version + ")";
    }
}
