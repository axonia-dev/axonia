package dev.axonia.knowledge.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends CrudRepository<AttachmentEntity, Long> {

    Optional<AttachmentEntity> findByOrganizationIdAndArtifactIdAndSessionIdAndSegmentId(String organizationId,
            Long artifactId, Long sessionId, Long segmentId);

    @Modifying
    @Query("DELETE FROM artifact_attachment WHERE organization_id = :organizationId AND session_id = :sessionId")
    void deleteByOrganizationIdAndSessionId(@Param("organizationId") String organizationId,
            @Param("sessionId") Long sessionId);
}
