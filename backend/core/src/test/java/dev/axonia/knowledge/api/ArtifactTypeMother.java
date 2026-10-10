package dev.axonia.knowledge.api;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtifactTypeMother {

    public ArtifactType.ArtifactTypeBuilder any() {

        return ArtifactType.builder().id(1L).label("Recording");
    }
}
