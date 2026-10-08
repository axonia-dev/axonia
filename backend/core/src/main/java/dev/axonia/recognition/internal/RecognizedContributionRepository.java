package dev.axonia.recognition.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import dev.axonia.recognition.ContributionType;

public interface RecognizedContributionRepository extends CrudRepository<RecognizedContributionEntity, Long> {

    Optional<RecognizedContributionEntity> findByOrganizationIdAndContributionTypeAndContributionId(
            String organizationId, ContributionType contributionType, Long contributionId);

    List<RecognizedContributionEntity> findByOrganizationIdAndContributionTypeAndContributionIdIn(String organizationId,
            ContributionType contributionType, Collection<Long> contributionIds);
}
