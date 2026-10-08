package dev.axonia.knowledge.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

public interface ArtifactRepository extends CrudRepository<ArtifactEntity, Long>, ArtifactSearchRepository {

    Optional<ArtifactEntity> findByOrganizationIdAndId(String organizationId, Long id);

    List<ArtifactEntity> findByOrganizationIdAndContributorUserId(String organizationId, String contributorUserId);
}
