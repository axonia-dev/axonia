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
@ToString
@Table("directory_group")
public class DirectoryGroupEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private String scimId;
    private String externalId;
    private String displayName;
    private String reconciliationKey;

    /**
     * When an administrator confirmed this is the group they meant, or null while the group is held. Null only ever
     * arises when a role binding was already waiting on the reconciliation key at the moment the directory pushed it; a
     * held group confers none of the roles bound to that key.
     */
    private Instant confirmedAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
