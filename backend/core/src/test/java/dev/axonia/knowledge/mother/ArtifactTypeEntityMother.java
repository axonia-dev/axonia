package dev.axonia.knowledge.mother;

import dev.axonia.knowledge.internal.ArtifactTypeEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtifactTypeEntityMother {

    public ArtifactTypeEntity.ArtifactTypeEntityBuilder any() {

        return ArtifactTypeEntity.builder().organizationId("default").label("Recording");
    }
}
