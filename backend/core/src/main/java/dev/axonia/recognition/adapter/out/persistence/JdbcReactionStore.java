package dev.axonia.recognition.adapter.out.persistence;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.application.port.out.ReactionStore;
import dev.axonia.recognition.domain.ReactionPlacement;
import dev.axonia.recognition.domain.ReactionPlacementKey;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcReactionStore implements ReactionStore {

    private final ReactionRepository reactions;

    @Override
    @Transactional(readOnly = true)
    public List<ReactionPlacement> placementsOn(String organizationId, ContributionType contributionType,
            Long contributionId) {
        return reactions.findByOrganizationIdAndContributionTypeAndContributionId(organizationId, contributionType,
                contributionId).stream().map(JdbcReactionStore::toPlacement).toList();
    }

    @Override
    @Transactional
    public boolean place(ReactionPlacement placement) {
        try {
            reactions.save(toEntity(placement));
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    @Override
    @Transactional
    public int remove(ReactionPlacementKey key) {
        return reactions.deletePlacement(key);
    }

    static ReactionPlacement toPlacement(ReactionEntity entity) {
        return ReactionPlacement.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .communityId(entity.getCommunityId()).contributionType(entity.getContributionType())
                .contributionId(entity.getContributionId()).reactorUserId(entity.getReactorUserId())
                .emoji(entity.getEmoji()).definitionId(entity.getDefinitionId()).createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static ReactionEntity toEntity(ReactionPlacement placement) {
        return ReactionEntity.builder().id(placement.id()).organizationId(placement.organizationId())
                .communityId(placement.communityId()).contributionType(placement.contributionType())
                .contributionId(placement.contributionId()).reactorUserId(placement.reactorUserId())
                .emoji(placement.emoji()).definitionId(placement.definitionId()).createdAt(placement.createdAt())
                .updatedAt(placement.updatedAt()).version(placement.version()).build();
    }
}
