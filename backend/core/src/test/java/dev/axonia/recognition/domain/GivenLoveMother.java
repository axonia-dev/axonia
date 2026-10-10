package dev.axonia.recognition.domain;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GivenLoveMother {

    public GivenLove.GivenLoveBuilder any() {

        return GivenLove.builder().organizationId("default").recognizedContributionId(1L).loverUserId("bob");
    }
}
