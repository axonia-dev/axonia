package dev.axonia.knowledge.domain;

import lombok.experimental.UtilityClass;

@UtilityClass
public class StoredAttachmentMother {

    public StoredAttachment.StoredAttachmentBuilder any() {

        return StoredAttachment.builder().organizationId("default").artifactId(1L).sessionId(100L).segmentId(7L)
                .attachedByUserId("dev-user");
    }
}
