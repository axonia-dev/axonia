package dev.axonia.knowledge.mother;

import dev.axonia.knowledge.ArtifactType;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtifactTypeMother {

    public ArtifactType.ArtifactTypeBuilder any() {

        return ArtifactType.builder().id(1L).label("Recording");
    }
}
