package dev.axonia.community.api;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CommunityMother {

    public Community.CommunityBuilder any() {

        return Community.builder().id(1L).slug("backend-guild").name("Backend Guild").kindId(1L).kind("Guild")
                .description("A guild for backend engineers").status(CommunityStatus.ACTIVE).memberCount(1L);
    }
}
