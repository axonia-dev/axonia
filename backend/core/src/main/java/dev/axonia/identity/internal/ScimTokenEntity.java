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
@Table("scim_token")
public class ScimTokenEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private String tokenHash;
    private String label;
    private Instant lastUsedAt;
    private Instant revokedAt;
    private Instant expiresAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
