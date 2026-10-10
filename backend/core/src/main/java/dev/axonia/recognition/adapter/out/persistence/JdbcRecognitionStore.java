package dev.axonia.recognition.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.application.port.out.RecognitionStore;
import dev.axonia.recognition.domain.Recognition;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcRecognitionStore implements RecognitionStore {

    private static final String RECOGNIZE_SQL = """
            INSERT INTO recognized_contribution (organization_id, contribution_type, contribution_id, community_id,
                                                 title, occurred_at, created_at, updated_at, version)
            VALUES (:org, :contributionType, :contributionId, :communityId, :title, :occurredAt, :now, :now, 0)
            ON CONFLICT (organization_id, contribution_type, contribution_id) DO NOTHING
            """;

    private final RecognizedContributionRepository recognizedContributionRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<Recognition> find(String organizationId, ContributionType contributionType, Long contributionId) {
        return recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId(organizationId,
                contributionType, contributionId).map(JdbcRecognitionStore::toRecognition);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Recognition> findAll(String organizationId, ContributionType contributionType,
            Collection<Long> contributionIds) {
        return recognizedContributionRepository
                .findByOrganizationIdAndContributionTypeAndContributionIdIn(organizationId, contributionType,
                        contributionIds)
                .stream().map(JdbcRecognitionStore::toRecognition).toList();
    }

    /**
     * Finds the recognition, creating it on the first love. A concurrent first love that stored it first is not an
     * error: {@code ON CONFLICT DO NOTHING} waits for it and the read after sees its row. Catching the unique violation
     * instead cannot work, since Postgres aborts the caller's transaction on it.
     */
    @Override
    @Transactional
    public Recognition recognize(Recognition candidate) {
        return find(candidate.organizationId(), candidate.contributionType(), candidate.contributionId())
                .orElseGet(() -> {
                    jdbc.update(RECOGNIZE_SQL, new MapSqlParameterSource("org", candidate.organizationId())
                            .addValue("contributionType", candidate.contributionType().name())
                            .addValue("contributionId", candidate.contributionId())
                            .addValue("communityId", candidate.communityId()).addValue("title", candidate.title())
                            .addValue("occurredAt", Timestamp.from(candidate.occurredAt()))
                            .addValue("now", Timestamp.from(clock.instant())));
                    return find(candidate.organizationId(), candidate.contributionType(), candidate.contributionId())
                            .orElseThrow();
                });
    }

    static Recognition toRecognition(RecognizedContributionEntity entity) {
        return Recognition.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .contributionType(entity.getContributionType()).contributionId(entity.getContributionId())
                .communityId(entity.getCommunityId()).title(entity.getTitle()).occurredAt(entity.getOccurredAt())
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static RecognizedContributionEntity toEntity(Recognition recognition) {
        return RecognizedContributionEntity.builder().id(recognition.id()).organizationId(recognition.organizationId())
                .contributionType(recognition.contributionType()).contributionId(recognition.contributionId())
                .communityId(recognition.communityId()).title(recognition.title()).occurredAt(recognition.occurredAt())
                .createdAt(recognition.createdAt()).updatedAt(recognition.updatedAt()).version(recognition.version())
                .build();
    }
}
