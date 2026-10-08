package dev.axonia.identity.internal;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.Entity;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString(exclude = "codeChallenge")
@Table("agent_connection")
public class AgentConnectionEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private String userId;
    private String clientId;
    private String clientName;
    private String clientHost;
    private String scopes;
    private String resource;
    private String redirectUri;
    private String codeChallenge;
    private Instant activatedAt;
    private Instant lastUsedAt;
    private Instant endsAt;
    private Instant revokedAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

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
}
