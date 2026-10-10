package dev.axonia.events.application.port.out;

import java.util.Collection;
import java.util.List;

import dev.axonia.events.domain.ActionItemCheckpointRecord;
import dev.axonia.events.domain.CheckpointOutcome;

public interface ActionItemCheckpointStore {

    List<ActionItemCheckpointRecord> findByOrganizationIdAndCommunityIdAndSessionId(String organizationId,
            Long communityId, Long sessionId);

    long countByOrganizationIdAndActionItemIdAndOutcome(String organizationId, Long actionItemId,
            CheckpointOutcome outcome);

    List<ActionItemCheckpointRecord> findByOrganizationIdAndActionItemIdInAndOutcome(String organizationId,
            Collection<Long> actionItemIds, CheckpointOutcome outcome);

    ActionItemCheckpointRecord save(ActionItemCheckpointRecord checkpoint);
}
