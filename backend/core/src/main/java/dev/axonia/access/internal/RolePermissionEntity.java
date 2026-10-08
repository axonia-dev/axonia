package dev.axonia.access.internal;

import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Table("role_permission")
public class RolePermissionEntity {
    private String permission; // the Permission.code() string
}
