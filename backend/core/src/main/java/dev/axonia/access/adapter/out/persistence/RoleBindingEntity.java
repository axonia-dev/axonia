package dev.axonia.access.adapter.out.persistence;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.shared.api.persistence.Entity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@Table("role_binding")
class RoleBindingEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private String subjectType; // "USER" | "GROUP"
    private String subjectId;
    private String subjectLabel;
    private Long roleId;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
