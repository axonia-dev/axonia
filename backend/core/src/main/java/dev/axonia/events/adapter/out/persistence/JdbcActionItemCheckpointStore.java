package dev.axonia.events.adapter.out.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.application.port.out.ActionItemCheckpointStore;
import dev.axonia.events.domain.ActionItemCheckpointRecord;
import dev.axonia.events.domain.CheckpointOutcome;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcActionItemCheckpointStore implements ActionItemCheckpointStore {

    private final ActionItemCheckpointRepository actionItemCheckpointRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ActionItemCheckpointRecord> findByOrganizationIdAndCommunityIdAndSessionId(String organizationId,
            Long communityId, Long sessionId) {

        return actionItemCheckpointRepository
                .findByOrganizationIdAndCommunityIdAndSessionId(organizationId, communityId, sessionId).stream()
                .map(JdbcActionItemCheckpointStore::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByOrganizationIdAndActionItemIdAndOutcome(String organizationId, Long actionItemId,
            CheckpointOutcome outcome) {

        return actionItemCheckpointRepository.countByOrganizationIdAndActionItemIdAndOutcome(organizationId,
                actionItemId, outcome);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActionItemCheckpointRecord> findByOrganizationIdAndActionItemIdInAndOutcome(String organizationId,
            Collection<Long> actionItemIds, CheckpointOutcome outcome) {

        return actionItemCheckpointRepository
                .findByOrganizationIdAndActionItemIdInAndOutcome(organizationId, actionItemIds, outcome).stream()
                .map(JdbcActionItemCheckpointStore::toRecord).toList();
    }

    @Override
    @Transactional
    public ActionItemCheckpointRecord save(ActionItemCheckpointRecord checkpoint) {

        return toRecord(actionItemCheckpointRepository.save(toEntity(checkpoint)));
    }

    static ActionItemCheckpointRecord toRecord(ActionItemCheckpointEntity entity) {

        return ActionItemCheckpointRecord.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .actionItemId(entity.getActionItemId()).sessionId(entity.getSessionId()).outcome(entity.getOutcome())
                .decidedByUserId(entity.getDecidedByUserId()).decidedAt(entity.getDecidedAt()).build();
    }

    static ActionItemCheckpointEntity toEntity(ActionItemCheckpointRecord checkpoint) {

        return ActionItemCheckpointEntity.builder().id(checkpoint.getId())
                .organizationId(checkpoint.getOrganizationId()).actionItemId(checkpoint.getActionItemId())
                .sessionId(checkpoint.getSessionId()).outcome(checkpoint.getOutcome())
                .decidedByUserId(checkpoint.getDecidedByUserId()).decidedAt(checkpoint.getDecidedAt()).build();
    }
}
