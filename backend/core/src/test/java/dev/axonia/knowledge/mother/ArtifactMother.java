package dev.axonia.knowledge.mother;

import java.util.List;

import dev.axonia.knowledge.Artifact;
import dev.axonia.knowledge.ArtifactStatus;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtifactMother {

    public Artifact.ArtifactBuilder any() {

        return Artifact.builder().id(1L).communityId(1L).typeId(1L).type("Recording").title("Resilience patterns talk")
                .description("How we handle retries").url("https://example.com/talk").tags(List.of("resilience"))
                .contributorUserId("dev-user").status(ArtifactStatus.ACTIVE);
    }
}
