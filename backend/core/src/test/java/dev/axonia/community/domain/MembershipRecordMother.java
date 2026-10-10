package dev.axonia.community.domain;

import dev.axonia.community.api.CommunityRole;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MembershipRecordMother {

    public MembershipRecord.MembershipRecordBuilder any() {
        return MembershipRecord.builder().organizationId("default").communityId(1L).userId("dev-user")
                .role(CommunityRole.MEMBER);
    }
}
