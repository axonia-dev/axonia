package dev.axonia.knowledge.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ArtifactRepository extends CrudRepository<ArtifactEntity, Long>, ArtifactSearchRepository {

    Optional<ArtifactEntity> findByOrganizationIdAndId(String organizationId, Long id);

    List<ArtifactEntity> findByOrganizationIdAndContributorUserId(String organizationId, String contributorUserId);

    @Query("""
            SELECT provenance_session_id AS session_id, COUNT(*) AS artifact_count,
                   COALESCE(SUM(love_count), 0) AS love_total
            FROM artifact
            WHERE organization_id = :organizationId AND community_id = :communityId AND status = 'ACTIVE'
              AND provenance_session_id IN (:sessionIds)
            GROUP BY provenance_session_id
            """)
    List<SessionArtifactTally> tallySessions(@Param("organizationId") String organizationId,
            @Param("communityId") Long communityId, @Param("sessionIds") Collection<Long> sessionIds);

    record SessionArtifactTally(Long sessionId, long artifactCount, long loveTotal) {
    }
}
