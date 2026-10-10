package dev.axonia.access.adapter.out.persistence;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RoleEntityMother {

    public RoleEntity.RoleEntityBuilder any() {
        return RoleEntity.builder().organizationId("default").name("Custom Role");
    }

    public RolePermissionEntity permission(String code) {
        return RolePermissionEntity.builder().permission(code).build();
    }
}
