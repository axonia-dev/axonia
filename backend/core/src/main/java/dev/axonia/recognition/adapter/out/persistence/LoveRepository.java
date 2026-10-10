package dev.axonia.recognition.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

public interface LoveRepository extends CrudRepository<LoveEntity, Long> {

    Optional<LoveEntity> findByOrganizationIdAndRecognizedContributionIdAndLoverUserId(String organizationId,
            Long recognizedContributionId, String loverUserId);

    List<LoveEntity> findByOrganizationIdAndRecognizedContributionId(String organizationId,
            Long recognizedContributionId);
}
