package dev.axonia.recognition.adapter.out.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.recognition.application.port.out.CustomReactionStore;
import dev.axonia.recognition.domain.CustomReaction;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcCustomReactionStore implements CustomReactionStore {

    private final ReactionDefinitionRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<CustomReaction> all(String organizationId) {
        return repository.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(JdbcCustomReactionStore::toCustomReaction).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomReaction> inState(String organizationId, String state) {
        return repository.findByOrganizationIdAndStateOrderByNameAsc(organizationId, state).stream()
                .map(JdbcCustomReactionStore::toCustomReaction).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomReaction> proposedBy(String organizationId, String state, String proposedByUserId) {
        return repository
                .findByOrganizationIdAndStateAndProposedByUserIdOrderByNameAsc(organizationId, state, proposedByUserId)
                .stream().map(JdbcCustomReactionStore::toCustomReaction).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomReaction> find(String organizationId, Long id) {
        return repository.findByOrganizationIdAndId(organizationId, id).map(JdbcCustomReactionStore::toCustomReaction);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean nameTaken(String organizationId, String name) {
        return repository.existsByOrganizationIdAndName(organizationId, name);
    }

    @Override
    @Transactional(readOnly = true)
    public int countInState(String organizationId, String state) {
        return repository.countByOrganizationIdAndState(organizationId, state);
    }

    @Override
    @Transactional(readOnly = true)
    public int countProposedBy(String organizationId, String state, String proposedByUserId) {
        return repository.countByOrganizationIdAndStateAndProposedByUserId(organizationId, state, proposedByUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> usageByDefinition(String organizationId) {
        return repository.countUsageByDefinition(organizationId).stream()
                .collect(Collectors.toMap(ReactionDefinitionRepository.DefinitionUsage::definitionId,
                        ReactionDefinitionRepository.DefinitionUsage::usageCount));
    }

    @Override
    @Transactional
    public CustomReaction save(CustomReaction reaction) {
        return toCustomReaction(repository.save(toEntity(reaction)));
    }

    @Override
    public void remove(CustomReaction reaction) {
        repository.delete(toEntity(reaction));
    }

    static CustomReaction toCustomReaction(ReactionDefinitionEntity entity) {
        return CustomReaction.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .name(entity.getName()).imageKey(entity.getImageKey()).state(entity.getState())
                .proposedByUserId(entity.getProposedByUserId()).approvedByUserId(entity.getApprovedByUserId())
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static ReactionDefinitionEntity toEntity(CustomReaction reaction) {
        return ReactionDefinitionEntity.builder().id(reaction.id()).organizationId(reaction.organizationId())
                .name(reaction.name()).imageKey(reaction.imageKey()).state(reaction.state())
                .proposedByUserId(reaction.proposedByUserId()).approvedByUserId(reaction.approvedByUserId())
                .createdAt(reaction.createdAt()).updatedAt(reaction.updatedAt()).version(reaction.version()).build();
    }
}
