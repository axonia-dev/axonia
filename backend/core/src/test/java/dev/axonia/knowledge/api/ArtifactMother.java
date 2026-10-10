package dev.axonia.knowledge.api;

import java.util.List;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtifactMother {

    public Artifact.ArtifactBuilder any() {

        return Artifact.builder().id(1L).communityId(1L).typeId(1L).type("Recording").title("Resilience patterns talk")
                .description("How we handle retries").url("https://example.com/talk").tags(List.of("resilience"))
                .contributorUserId("dev-user").status(ArtifactStatus.ACTIVE);
    }
}
