package dev.axonia.community.adapter.out.persistence;

import dev.axonia.community.api.CommunityStatus;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CommunityEntityMother {

    public CommunityEntity.CommunityEntityBuilder any() {

        return CommunityEntity.builder().organizationId("default").slug("backend-guild").name("Backend Guild")
                .kindId(1L).description("A guild for backend engineers").status(CommunityStatus.ACTIVE);
    }
}
