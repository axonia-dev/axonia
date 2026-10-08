package dev.axonia.access.internal;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.Entity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@Table("role")
public class RoleEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private String name;
    private String description;

    @Builder.Default
    private boolean system = false;

    private String slug;

    @MappedCollection(idColumn = "role_id")
    @Builder.Default
    private Set<RolePermissionEntity> permissions = new HashSet<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
