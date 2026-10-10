package dev.axonia.community.api;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MembershipMother {

    public Membership.MembershipBuilder any() {

        return Membership.builder().id(1L).communityId(1L).userId("dev-user").role(CommunityRole.MEMBER);
    }
}
