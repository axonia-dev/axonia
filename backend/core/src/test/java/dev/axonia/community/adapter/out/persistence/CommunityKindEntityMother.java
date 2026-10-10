package dev.axonia.community.adapter.out.persistence;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CommunityKindEntityMother {

    public CommunityKindEntity.CommunityKindEntityBuilder any() {

        return CommunityKindEntity.builder().organizationId("default").label("Guild");
    }
}
