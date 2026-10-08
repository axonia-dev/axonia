package dev.axonia.community.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

public interface CommunityKindRepository extends CrudRepository<CommunityKindEntity, Long> {

    List<CommunityKindEntity> findByOrganizationIdOrderByLabelAsc(String organizationId);

    Optional<CommunityKindEntity> findByOrganizationIdAndId(String organizationId, Long id);

    boolean existsByOrganizationIdAndLabel(String organizationId, String label);
}
