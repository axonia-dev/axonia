package dev.axonia.recognition.adapter.out.persistence;

import lombok.experimental.UtilityClass;

@UtilityClass
public class LoveEntityMother {

    public LoveEntity.LoveEntityBuilder any() {

        return LoveEntity.builder().organizationId("default").recognizedContributionId(1L).loverUserId("bob");
    }
}
