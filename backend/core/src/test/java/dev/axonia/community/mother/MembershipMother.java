package dev.axonia.community.mother;

import dev.axonia.community.CommunityRole;
import dev.axonia.community.Membership;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MembershipMother {

    public Membership.MembershipBuilder any() {

        return Membership.builder().id(1L).communityId(1L).userId("dev-user").role(CommunityRole.MEMBER);
    }
}
