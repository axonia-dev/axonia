package dev.axonia.community.mother;

import dev.axonia.community.CommunityKind;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CommunityKindMother {

    public CommunityKind.CommunityKindBuilder any() {

        return CommunityKind.builder().id(1L).label("Guild");
    }
}
