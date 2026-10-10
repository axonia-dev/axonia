package dev.axonia.community.domain;

import dev.axonia.community.api.CommunityStatus;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CommunityRecordMother {

    public CommunityRecord.CommunityRecordBuilder any() {
        return CommunityRecord.builder().organizationId("default").slug("backend-guild").name("Backend Guild")
                .kindId(1L).description("A guild for backend engineers").status(CommunityStatus.ACTIVE);
    }
}
