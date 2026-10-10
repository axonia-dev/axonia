package dev.axonia.knowledge.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ArtifactTypeRepository extends CrudRepository<ArtifactTypeEntity, Long> {

    List<ArtifactTypeEntity> findByOrganizationIdOrderByLabelAsc(String organizationId);

    Optional<ArtifactTypeEntity> findByOrganizationIdAndId(String organizationId, Long id);

    List<ArtifactTypeEntity> findByOrganizationIdAndIdIn(String organizationId, Collection<Long> ids);

    boolean existsByOrganizationIdAndLabel(String organizationId, String label);

    @Query("SELECT count(*) FROM artifact WHERE type_id = :typeId")
    long countArtifactRefs(@Param("typeId") Long typeId);

    @Query("SELECT type_id, COUNT(*) AS artifact_count FROM artifact WHERE organization_id = :organizationId GROUP BY type_id")
    List<TypeArtifactCount> countArtifactsByType(@Param("organizationId") String organizationId);

    record TypeArtifactCount(Long typeId, long artifactCount) {
    }

    @Modifying
    @Query("UPDATE artifact SET type_id = :target WHERE type_id = :source")
    void reassignArtifactRefs(@Param("source") Long source, @Param("target") Long target);
}
