package dev.axonia.organization.adapter.out.persistence;

import dev.axonia.organization.api.OrganizationKind;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OrganizationEntityMother {

    public OrganizationEntity.OrganizationEntityBuilder any() {
        return OrganizationEntity.builder().id("default").name("default").kind(OrganizationKind.ENTERPRISE);
    }
}
