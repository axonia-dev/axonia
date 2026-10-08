package dev.axonia.community.mother;

import dev.axonia.community.CommunityRole;
import dev.axonia.community.internal.MembershipEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MembershipEntityMother {

    public MembershipEntity.MembershipEntityBuilder any() {

        return MembershipEntity.builder().organizationId("default").communityId(1L).userId("dev-user")
                .role(CommunityRole.MEMBER);
    }
}
