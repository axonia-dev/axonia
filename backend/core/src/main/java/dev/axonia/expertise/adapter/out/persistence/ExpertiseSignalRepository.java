package dev.axonia.expertise.adapter.out.persistence;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

public interface ExpertiseSignalRepository extends CrudRepository<ExpertiseSignalEntity, Long> {

    List<ExpertiseSignalEntity> findByOrganizationIdAndUserId(String organizationId, String userId);

    List<ExpertiseSignalEntity> findByOrganizationIdAndCommunityIdAndTag(String organizationId, Long communityId,
            String tag);
}
