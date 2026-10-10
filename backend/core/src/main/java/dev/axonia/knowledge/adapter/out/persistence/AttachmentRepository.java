package dev.axonia.knowledge.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

public interface AttachmentRepository extends CrudRepository<AttachmentEntity, Long> {

    Optional<AttachmentEntity> findByOrganizationIdAndArtifactIdAndSessionIdAndSegmentId(String organizationId,
            Long artifactId, Long sessionId, Long segmentId);
}
