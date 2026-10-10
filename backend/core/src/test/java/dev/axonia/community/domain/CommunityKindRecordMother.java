package dev.axonia.community.domain;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CommunityKindRecordMother {

    public CommunityKindRecord.CommunityKindRecordBuilder any() {
        return CommunityKindRecord.builder().organizationId("default").label("Guild");
    }
}
