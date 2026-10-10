package dev.axonia.knowledge.domain;

import lombok.experimental.UtilityClass;

@UtilityClass
public class StoredArtifactTypeMother {

    public StoredArtifactType.StoredArtifactTypeBuilder any() {

        return StoredArtifactType.builder().organizationId("default").label("Recording");
    }
}
