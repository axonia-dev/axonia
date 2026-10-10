package dev.axonia.knowledge.adapter.out.persistence;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtifactTypeEntityMother {

    public ArtifactTypeEntity.ArtifactTypeEntityBuilder any() {

        return ArtifactTypeEntity.builder().organizationId("default").label("Recording");
    }
}
