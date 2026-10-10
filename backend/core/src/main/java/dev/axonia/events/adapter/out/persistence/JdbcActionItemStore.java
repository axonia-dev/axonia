package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.application.ActionItemFilter;
import dev.axonia.events.application.port.out.ActionItemStore;
import dev.axonia.events.domain.ActionItemRecord;
import dev.axonia.events.domain.ActionItemTag;
import dev.axonia.events.domain.LateBindingPin;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcActionItemStore implements ActionItemStore {

    private final ActionItemRepository actionItemRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ActionItemRecord> findByOrganizationIdAndId(String organizationId, Long id) {

        return actionItemRepository.findByOrganizationIdAndId(organizationId, id).map(JdbcActionItemStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActionItemRecord> findByOrganizationIdAndIdIn(String organizationId, Collection<Long> ids) {

        return actionItemRepository.findByOrganizationIdAndIdIn(organizationId, ids).stream()
                .map(JdbcActionItemStore::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActionItemRecord> findOpenByCheckpointSession(String organizationId, Long communityId, Long sessionId) {

        return actionItemRepository.findOpenByCheckpointSession(organizationId, communityId, sessionId).stream()
                .map(JdbcActionItemStore::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActionItemRecord> findByOriginSessionId(String organizationId, Long communityId, Long sessionId) {

        return actionItemRepository.findByOriginSessionId(organizationId, communityId, sessionId).stream()
                .map(JdbcActionItemStore::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ActionItemRecord> findFiltered(String organizationId, Long communityId, ActionItemFilter filter,
            Pageable pageable) {

        return actionItemRepository.findFiltered(organizationId, communityId, filter, pageable)
                .map(JdbcActionItemStore::toRecord);
    }

    @Override
    public void pinLateBound(LateBindingPin pin, Instant scheduledAt, Instant at) {

        actionItemRepository.pinLateBound(pin, scheduledAt, at);
    }

    @Override
    public void clearPinsFor(String organizationId, Long communityId, Long sessionId, Instant at) {

        actionItemRepository.clearPinsFor(organizationId, communityId, sessionId, at);
    }

    @Override
    @Transactional
    public ActionItemRecord save(ActionItemRecord item) {

        return toRecord(actionItemRepository.save(toEntity(item)));
    }

    @Override
    public void delete(ActionItemRecord item) {

        actionItemRepository.delete(toEntity(item));
    }

    static ActionItemRecord toRecord(ActionItemEntity entity) {

        return ActionItemRecord.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .communityId(entity.getCommunityId()).title(entity.getTitle()).description(entity.getDescription())
                .raisedByUserId(entity.getRaisedByUserId()).assigneeUserId(entity.getAssigneeUserId())
                .dueDate(entity.getDueDate()).originSessionId(entity.getOriginSessionId())
                .checkpointSessionId(entity.getCheckpointSessionId()).outcome(entity.getOutcome())
                .tags(RecordMapping.set(entity.getTags(), t -> ActionItemTag.builder().tag(t.getTag()).build()))
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static ActionItemEntity toEntity(ActionItemRecord item) {

        return ActionItemEntity.builder().id(item.getId()).organizationId(item.getOrganizationId())
                .communityId(item.getCommunityId()).title(item.getTitle()).description(item.getDescription())
                .raisedByUserId(item.getRaisedByUserId()).assigneeUserId(item.getAssigneeUserId())
                .dueDate(item.getDueDate()).originSessionId(item.getOriginSessionId())
                .checkpointSessionId(item.getCheckpointSessionId()).outcome(item.getOutcome())
                .tags(RecordMapping.set(item.getTags(), t -> ActionItemTagRef.builder().tag(t.getTag()).build()))
                .createdAt(item.getCreatedAt()).updatedAt(item.getUpdatedAt()).version(item.getVersion()).build();
    }
}
