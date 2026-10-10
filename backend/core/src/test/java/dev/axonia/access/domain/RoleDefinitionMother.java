package dev.axonia.access.domain;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RoleDefinitionMother {

    public RoleDefinition.RoleDefinitionBuilder any() {
        return RoleDefinition.builder().organizationId("default").name("Custom Role");
    }

    public String permission(String code) {
        return code;
    }
}
