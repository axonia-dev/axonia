package dev.axonia.knowledge.adapter.out.persistence;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AttachmentEntityMother {

    public AttachmentEntity.AttachmentEntityBuilder any() {

        return AttachmentEntity.builder().organizationId("default").artifactId(1L).sessionId(100L).segmentId(7L)
                .attachedByUserId("dev-user");
    }
}
