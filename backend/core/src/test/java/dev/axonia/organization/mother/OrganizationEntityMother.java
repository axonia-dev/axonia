package dev.axonia.organization.mother;

import dev.axonia.organization.OrganizationKind;
import dev.axonia.organization.internal.OrganizationEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OrganizationEntityMother {

    public OrganizationEntity.OrganizationEntityBuilder any() {
        return OrganizationEntity.builder().id("default").name("default").kind(OrganizationKind.ENTERPRISE);
    }
}
