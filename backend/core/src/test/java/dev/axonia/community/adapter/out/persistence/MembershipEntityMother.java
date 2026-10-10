package dev.axonia.community.adapter.out.persistence;

import dev.axonia.community.api.CommunityRole;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MembershipEntityMother {

    public MembershipEntity.MembershipEntityBuilder any() {

        return MembershipEntity.builder().organizationId("default").communityId(1L).userId("dev-user")
                .role(CommunityRole.MEMBER);
    }
}
