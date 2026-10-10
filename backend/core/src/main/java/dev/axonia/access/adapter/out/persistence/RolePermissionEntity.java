package dev.axonia.access.adapter.out.persistence;

import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Table("role_permission")
class RolePermissionEntity {
    private String permission; // the Permission.code() string
}
