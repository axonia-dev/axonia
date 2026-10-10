package dev.axonia.community.domain;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CommunityKindMother {

    public CommunityKind.CommunityKindBuilder any() {

        return CommunityKind.builder().id(1L).label("Guild");
    }
}
