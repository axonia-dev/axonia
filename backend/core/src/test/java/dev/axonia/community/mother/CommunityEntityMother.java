package dev.axonia.community.mother;

import dev.axonia.community.CommunityStatus;
import dev.axonia.community.internal.CommunityEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CommunityEntityMother {

    public CommunityEntity.CommunityEntityBuilder any() {

        return CommunityEntity.builder().organizationId("default").slug("backend-guild").name("Backend Guild")
                .kindId(1L).description("A guild for backend engineers").status(CommunityStatus.ACTIVE);
    }
}
