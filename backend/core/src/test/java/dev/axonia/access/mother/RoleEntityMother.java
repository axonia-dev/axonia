package dev.axonia.access.mother;

import dev.axonia.access.internal.RoleEntity;
import dev.axonia.access.internal.RolePermissionEntity;
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
