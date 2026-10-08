package dev.axonia.community.mother;

import dev.axonia.community.internal.CommunityKindEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CommunityKindEntityMother {

    public CommunityKindEntity.CommunityKindEntityBuilder any() {

        return CommunityKindEntity.builder().organizationId("default").label("Guild");
    }
}
