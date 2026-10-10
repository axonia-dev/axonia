package dev.axonia.events.application;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.ActionItem;
import dev.axonia.events.api.ActionItemOutcome;
import dev.axonia.events.api.ActionItemReopened;
import dev.axonia.events.api.ActionItems;
import dev.axonia.events.api.RaiseActionItemRequest;
import dev.axonia.events.application.port.out.ActionItemStore;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.ActionItemRecord;
import dev.axonia.events.domain.ActionItemTag;
import dev.axonia.events.domain.Descriptions;
import dev.axonia.events.domain.Tags;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * An action item's own lifecycle: raised, closed, dropped, reopened. Unlike a topic, it never occupies a slot, so
 * nothing here touches a session's schedule - the absence is the point, not an oversight.
 */
@Service
@RequiredArgsConstructor
public class ActionItemService implements ActionItems {

    private final ActionItemStore actionItemStore;
    private final ActionItemReadModel actionItemReadModel;
    private final ActionItemCloser actionItemCloser;
    private final CommunityAccess communityAccess;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final ApplicationEventPublisher eventPublisher;
    private final TopicMemberResolver topicMemberResolver;
    private final SessionCommunityGuard sessionCommunityGuard;
    private final TopicAuthorizer topicAuthorizer;
    private final SessionStore sessionStore;
    private final Clock clock;

    @Override
    @Transactional
    public ActionItem raise(String slug, RaiseActionItemRequest request) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        requireMember(communityId);
        sessionCommunityGuard.requireSessionInCommunity(organizationId, request.originSessionId(), communityId);
        sessionCommunityGuard.requireUpcomingSessionInCommunity(organizationId, request.checkpointSessionId(),
                communityId);

        var cleanedTags = Tags.clean(request.tags());
        var assigneeUserId = topicMemberResolver.resolveAssignee(communityId, request.assignee());
        var saved = actionItemStore
                .save(ActionItemRecord.builder().organizationId(organizationId).communityId(communityId)
                        .title(request.title()).description(Descriptions.orEmpty(request.description()))
                        .raisedByUserId(currentUser.userId()).assigneeUserId(assigneeUserId).dueDate(request.dueDate())
                        .originSessionId(request.originSessionId()).checkpointSessionId(request.checkpointSessionId())
                        .outcome(ActionItemOutcome.OPEN).tags(tagRefs(cleanedTags)).build());

        return actionItemReadModel.single(saved);
    }

    @Transactional(readOnly = true)
    public Page<ActionItem> list(String slug, ActionItemQuery query, Pageable pageable) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        return actionItemReadModel.list(ActionItemListQuery.builder().organizationId(organizationId)
                .communityId(communityId).outcome(query.outcome()).originSessionId(query.originSessionId())
                .checkpointSessionId(query.checkpointSessionId()).build(), pageable);
    }

    /**
     * Manager-only, not assignee-or-manager like {@code close}/{@code drop}/{@code reopen}: {@code close} publishes
     * {@code ActionItemClosed} (via {@link dev.axonia.events.application.ActionItemCloser}) carrying the item's tags at
     * closing time, and the expertise system credits the assignee for exactly those tags. An assignee who could edit
     * their own item could re-tag it into any expertise area immediately before closing it and award themselves credit
     * for expertise they don't have - so do not widen this to assignee-or-manager by copying {@code TopicService}'s
     * pattern without re-checking that.
     *
     * <p>
     * It is not the only route to self-awarded credit, and nothing here should be read as claiming so: {@link #raise}
     * needs community membership alone and takes {@code assignee} and {@code tags} in one request, so any member can
     * raise a self-assigned item in any tag and close it. That route predates this aggregate - {@code TopicService} had
     * the same shape - and whether to narrow it is a product decision, not something this method's authorization
     * settles.
     *
     * <p>
     * Also refused once the item is no longer {@code OPEN}, for a different reason: {@code checkpointSessionId} is the
     * session that answered it, part of the historical record once closed. A closed item is a record of what happened;
     * correcting one means reopen, edit, close - never a direct rewrite of a settled row.
     */
    @Transactional
    public ActionItem update(Long id, UpdateActionItemRequest request) {

        var organizationId = organizationContext.organizationId();
        var entity = findOrThrow(organizationId, id);
        topicAuthorizer.requireTopicManagementPermission(entity.getCommunityId(), currentUser.userId());
        if (entity.getOutcome() != ActionItemOutcome.OPEN) {
            throw new ConflictException("NOT_OPEN", "Only an open action item may be edited");
        }
        // Only a checkpoint that actually moves is held to the upcoming rule: close's pinLateBound pins open items to
        // the session it just held, so re-sending that stored id on a title edit would otherwise be refused forever.
        if (!Objects.equals(request.checkpointSessionId(), entity.getCheckpointSessionId())) {
            sessionCommunityGuard.requireUpcomingSessionInCommunity(organizationId, request.checkpointSessionId(),
                    entity.getCommunityId());
        }

        var assigneeUserId = topicMemberResolver.resolveAssignee(entity.getCommunityId(), request.assignee());
        var saved = actionItemStore
                .save(entity.toBuilder().title(request.title()).description(Descriptions.orEmpty(request.description()))
                        .tags(editedTags(entity, request.tags())).assigneeUserId(assigneeUserId)
                        .dueDate(request.dueDate()).checkpointSessionId(request.checkpointSessionId()).build());
        return actionItemReadModel.single(saved);
    }

    @Transactional
    public void remove(Long id) {

        var organizationId = organizationContext.organizationId();
        var entity = findOrThrow(organizationId, id);
        topicAuthorizer.requireTopicManagementPermission(entity.getCommunityId(), currentUser.userId());
        if (entity.getOutcome() != ActionItemOutcome.OPEN) {
            throw new ConflictException("NOT_OPEN", "Only an open action item may be deleted");
        }
        actionItemStore.delete(entity);
    }

    @Transactional
    public ActionItem close(Long id) {

        var organizationId = organizationContext.organizationId();
        var entity = findOrThrow(organizationId, id);
        if (entity.getOutcome() != ActionItemOutcome.OPEN) {
            throw new ConflictException("ALREADY_CLOSED", "This action item is already closed");
        }
        topicAuthorizer.requireAssigneeOrManager(entity.getCommunityId(), entity.getAssigneeUserId(),
                currentUser.userId());
        var saved = actionItemCloser.closeAndCredit(pinnedToItsCheckpoint(entity));
        return actionItemReadModel.single(saved);
    }

    /**
     * Settles an item as work the community will not do. Reachable from DONE as well as OPEN, because the row's settle
     * control cycles open, done, dropped: the room saying "done" and then "not after all" is a correction, and the
     * credit closing granted has to come back with it. Only an already-dropped item has nothing left to answer.
     */
    @Transactional
    public ActionItem drop(Long id) {

        var organizationId = organizationContext.organizationId();
        var entity = findOrThrow(organizationId, id);
        if (entity.getOutcome() == ActionItemOutcome.DROPPED) {
            throw new ConflictException("ALREADY_DROPPED", "This action item is already dropped");
        }
        topicAuthorizer.requireAssigneeOrManager(entity.getCommunityId(), entity.getAssigneeUserId(),
                currentUser.userId());
        var saved = actionItemStore
                .save(pinnedToItsCheckpoint(entity).toBuilder().outcome(ActionItemOutcome.DROPPED).build());
        if (entity.getOutcome() == ActionItemOutcome.DONE) {
            eventPublisher.publishEvent(new ActionItemReopened(saved.getId(), saved.getCommunityId()));
        }
        return actionItemReadModel.single(saved);
    }

    /**
     * Pins an item to the session it is being answered at, before it settles. A settled item no longer late-binds, so
     * an item that reached its session only by late-binding would otherwise leave that session's list the instant it
     * was settled from it - and the press that settled it could only be undone by hunting the item down from when it
     * was first raised. Pinning first is what makes it stay as that session's record, and {@link #reopen} keeping the
     * pin is what makes the undo land back in the same place. An item with no upcoming session to name settles
     * unpinned, because there is no record for it to stay on.
     */
    private ActionItemRecord pinnedToItsCheckpoint(ActionItemRecord entity) {

        if (entity.getCheckpointSessionId() != null) {
            return entity;
        }
        return sessionStore.findSoonestUpcoming(entity.getOrganizationId(), entity.getCommunityId(), clock.instant())
                .map(session -> entity.toBuilder().checkpointSessionId(session.getId()).build()).orElse(entity);
    }

    @Transactional
    public ActionItem reopen(Long id) {

        var organizationId = organizationContext.organizationId();
        var entity = findOrThrow(organizationId, id);
        if (entity.getOutcome() == ActionItemOutcome.OPEN) {
            throw new ConflictException("NOT_CLOSED", "Only a closed action item can be reopened");
        }
        topicAuthorizer.requireAssigneeOrManager(entity.getCommunityId(), entity.getAssigneeUserId(),
                currentUser.userId());
        var wasDone = entity.getOutcome() == ActionItemOutcome.DONE;
        var saved = actionItemStore.save(entity.toBuilder().outcome(ActionItemOutcome.OPEN).build());
        if (wasDone) {
            eventPublisher.publishEvent(new ActionItemReopened(saved.getId(), saved.getCommunityId()));
        }
        return actionItemReadModel.single(saved);
    }

    private void requireMember(long communityId) {

        if (!communityAccess.isMember(communityId, currentUser.userId())) {
            throw new ForbiddenException("Community membership required");
        }
    }

    private ActionItemRecord findOrThrow(String organizationId, Long id) {

        return actionItemStore.findByOrganizationIdAndId(organizationId, id)
                .orElseThrow(() -> new NotFoundException("Action item not found: " + id));
    }

    /**
     * A null {@code tags} means unchanged, an empty one clears. The edit form omits the field entirely, and the tags an
     * item carries at closing time are exactly what the expertise graph credits the assignee for - so treating the
     * absent field as "none" would silently destroy that credit on any unrelated edit.
     */
    private Set<ActionItemTag> editedTags(ActionItemRecord entity, List<String> requested) {

        return requested == null ? entity.getTags() : tagRefs(Tags.clean(requested));
    }

    private Set<ActionItemTag> tagRefs(Set<String> tags) {

        return tags.stream().map(t -> ActionItemTag.builder().tag(t).build())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
