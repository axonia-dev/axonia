package dev.axonia.recognition.mother;

import dev.axonia.recognition.internal.LoveEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class LoveEntityMother {

    public LoveEntity.LoveEntityBuilder any() {

        return LoveEntity.builder().organizationId("default").recognizedContributionId(1L).loverUserId("bob");
    }
}
