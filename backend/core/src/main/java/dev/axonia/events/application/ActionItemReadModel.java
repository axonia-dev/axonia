package dev.axonia.events.application;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.ActionItem;
import dev.axonia.events.api.ActionItemOutcome;
import dev.axonia.events.api.Checkpoint;
import dev.axonia.events.application.port.out.ActionItemCheckpointStore;
import dev.axonia.events.application.port.out.ActionItemStore;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.UserProfileAccess;
import dev.axonia.events.domain.ActionItemCheckpointRecord;
import dev.axonia.events.domain.ActionItemRecord;
import dev.axonia.events.domain.CheckpointOutcome;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

/** Turns a persisted action item into the {@link ActionItem} read model: checkpoint, carry count, display names. */
@Component
@RequiredArgsConstructor
public class ActionItemReadModel {

    private final ActionItemCheckpointStore actionItemCheckpointStore;
    private final CheckpointResolver checkpointResolver;
    private final UserProfileAccess userProfileAccess;
    private final TopicAuthorizer topicAuthorizer;
    private final ActionItemMapper actionItemMapper;
    private final CurrentUser currentUser;
    private final ActionItemStore actionItemStore;

    @Transactional(readOnly = true)
    public ActionItem single(ActionItemRecord e) {

        var organizationId = e.getOrganizationId();
        var userId = currentUser.userId();
        var checkpoint = checkpointResolver.resolve(organizationId, e);
        var carryCount = actionItemCheckpointStore.countByOrganizationIdAndActionItemIdAndOutcome(organizationId,
                e.getId(), CheckpointOutcome.CARRIED);
        var raisedByDisplayName = userProfileAccess.displayName(organizationId, e.getRaisedByUserId());
        var assigneeDisplayName = e.getAssigneeUserId() == null
                ? null
                : userProfileAccess.displayName(organizationId, e.getAssigneeUserId());
        var canManage = topicAuthorizer.canManageInCommunity(e.getCommunityId(), userId);
        return toActionItem(e,
                ActionItemDecoration.builder().checkpoint(checkpoint).carryCount((int) carryCount)
                        .raisedByDisplayName(raisedByDisplayName).assigneeDisplayName(assigneeDisplayName)
                        .canManage(canManage).build());
    }

    /**
     * A page of a community's action items. The list route is always scoped to one community's slug, so a page here is
     * always homogeneous in {@code communityId} - which is what lets manage rights resolve once for the whole page
     * rather than once per distinct community the way {@code TopicReadModel.page} has to. Carry counts and display
     * names are still batched over the page's own ids, and the checkpoint itself resolves through
     * {@link CheckpointResolver#resolveAll}, never one {@link CheckpointResolver#resolve} call per row.
     */
    @Transactional(readOnly = true)
    public Page<ActionItem> list(ActionItemListQuery query, Pageable pageable) {

        var organizationId = query.organizationId();
        var userId = currentUser.userId();
        var filter = new ActionItemFilter(query.outcome(), query.originSessionId(), query.checkpointSessionId());
        var page = actionItemStore.findFiltered(organizationId, query.communityId(), filter, pageable);
        var content = page.getContent();
        var ids = content.stream().map(ActionItemRecord::getId).toList();

        var carryCounts = actionItemCheckpointStore
                .findByOrganizationIdAndActionItemIdInAndOutcome(organizationId, ids, CheckpointOutcome.CARRIED)
                .stream()
                .collect(Collectors.groupingBy(ActionItemCheckpointRecord::getActionItemId, Collectors.counting()));

        var personIds = content.stream().flatMap(e -> Stream.of(e.getRaisedByUserId(), e.getAssigneeUserId()))
                .filter(Objects::nonNull).distinct().toList();
        var displayNames = userProfileAccess.displayNames(organizationId, personIds);

        var canManage = topicAuthorizer.canManageInCommunity(query.communityId(), userId);
        var checkpoints = checkpointResolver.resolveAll(organizationId, query.communityId(), content);

        return page.map(e -> toActionItem(e,
                ActionItemDecoration.builder().checkpoint(checkpoints.get(e.getId()))
                        .carryCount(carryCounts.getOrDefault(e.getId(), 0L).intValue())
                        .raisedByDisplayName(displayNames.getOrDefault(e.getRaisedByUserId(), e.getRaisedByUserId()))
                        .assigneeDisplayName(e.getAssigneeUserId() == null
                                ? null
                                : displayNames.getOrDefault(e.getAssigneeUserId(), e.getAssigneeUserId()))
                        .canManage(canManage).build()));
    }

    /** Per-item read-model context computed around the entity: checkpoint, carry count, labels, manage rights. */
    @Builder
    private record ActionItemDecoration(Checkpoint checkpoint, int carryCount, String raisedByDisplayName,
            String assigneeDisplayName, boolean canManage) {
    }

    private ActionItem toActionItem(ActionItemRecord e, ActionItemDecoration decoration) {

        var userId = currentUser.userId();
        return ActionItem.builder().id(e.getId()).communityId(e.getCommunityId()).title(e.getTitle())
                .description(e.getDescription()).tags(actionItemMapper.tags(e)).raisedByUserId(e.getRaisedByUserId())
                .raisedByDisplayName(decoration.raisedByDisplayName()).assigneeUserId(e.getAssigneeUserId())
                .assigneeDisplayName(decoration.assigneeDisplayName()).dueDate(e.getDueDate())
                .originSessionId(e.getOriginSessionId()).checkpoint(decoration.checkpoint())
                .carryCount(decoration.carryCount()).outcome(e.getOutcome())
                .canClose(canClose(e, decoration.canManage(), userId))
                .canReopen(canReopen(e, decoration.canManage(), userId))
                .canEdit(decoration.canManage() && e.getOutcome() == ActionItemOutcome.OPEN)
                .canDelete(decoration.canManage() && e.getOutcome() == ActionItemOutcome.OPEN)
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).build();
    }

    // An action item can be closed by whoever manages topics, or by its own assignee, while it is still open.
    private boolean canClose(ActionItemRecord e, boolean canManage, String userId) {

        return e.getOutcome() == ActionItemOutcome.OPEN
                && TopicAuthorizer.assigneeOrManager(canManage, e.getAssigneeUserId(), userId);
    }

    // The inverse of canClose: a DONE or DROPPED item can be reopened back to open, by the same people.
    private boolean canReopen(ActionItemRecord e, boolean canManage, String userId) {

        return e.getOutcome() != ActionItemOutcome.OPEN
                && TopicAuthorizer.assigneeOrManager(canManage, e.getAssigneeUserId(), userId);
    }
}
