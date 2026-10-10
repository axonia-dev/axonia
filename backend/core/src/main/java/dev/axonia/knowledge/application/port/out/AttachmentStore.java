package dev.axonia.knowledge.application.port.out;

import java.util.Optional;

import dev.axonia.knowledge.domain.StoredAttachment;

public interface AttachmentStore {

    Optional<StoredAttachment> find(String organizationId, Long artifactId, Long sessionId, Long segmentId);

    StoredAttachment save(StoredAttachment attachment);

    void delete(StoredAttachment attachment);
}
