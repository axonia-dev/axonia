package dev.axonia.organization.domain;

import dev.axonia.organization.api.OrganizationKind;
import dev.axonia.organization.api.OrganizationLifecycleState;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RegistryEntryMother {

    public RegistryEntry.RegistryEntryBuilder any() {
        return RegistryEntry.builder().id("default").name("default").kind(OrganizationKind.ENTERPRISE)
                .lifecycleState(OrganizationLifecycleState.ACTIVE).communicationsEnabled(true);
    }
}
