package dev.axonia.knowledge.mother;

import dev.axonia.knowledge.internal.AttachmentEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AttachmentEntityMother {

    public AttachmentEntity.AttachmentEntityBuilder any() {

        return AttachmentEntity.builder().organizationId("default").artifactId(1L).sessionId(100L).segmentId(7L)
                .attachedByUserId("dev-user");
    }
}
