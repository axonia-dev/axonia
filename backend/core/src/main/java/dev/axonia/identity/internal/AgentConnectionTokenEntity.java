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
@ToString(exclude = "tokenHash")
@Table("agent_connection_token")
public class AgentConnectionTokenEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private Long connectionId;
    private AgentConnectionTokenKind kind;
    private String tokenHash;
    private Instant expiresAt;
    private Instant consumedAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    public boolean isLiveAt(Instant now) {
        return expiresAt.isAfter(now);
    }
}
