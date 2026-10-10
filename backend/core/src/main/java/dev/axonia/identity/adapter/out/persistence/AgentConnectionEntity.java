package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.shared.api.persistence.Entity;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString(exclude = "codeChallenge")
@Table("agent_connection")
class AgentConnectionEntity implements Entity {

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
}
