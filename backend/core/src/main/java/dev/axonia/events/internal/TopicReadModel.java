package dev.axonia.events.internal;

import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.CurrentUser;
import dev.axonia.events.ScheduledFor;
import dev.axonia.events.Topic;
import dev.axonia.events.TopicStatus;
import dev.axonia.events.UserProfileAccess;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

/** Turns persisted topics into the {@link Topic} read model: schedule, vote counts, display names, manage rights. */
@Component
@RequiredArgsConstructor
public class TopicReadModel {

    private final TopicVoteRepository topicVoteRepository;
    private final UserProfileAccess userProfileAccess;
    private final TopicAuthorizer topicAuthorizer;
    private final TopicMapper topicMapper;
    private final CurrentUser currentUser;
    private final TopicRepository topicRepository;

    @Transactional(readOnly = true)
    public Topic single(TopicEntity e, ScheduledFor scheduledFor) {

        var orgId = e.getOrganizationId();
        var userId = currentUser.userId();
        var voteCount = topicVoteRepository.countByOrganizationIdAndTopicId(orgId, e.getId());
        var hasVoted = topicVoteRepository.existsByOrganizationIdAndTopicIdAndUserId(orgId, e.getId(), userId);
        var displayName = userProfileAccess.displayName(orgId, e.getProposerUserId());
        return toTopic(e,
                TopicDecoration.builder().scheduledFor(scheduledFor).voteCount(voteCount).hasVoted(hasVoted)
                        .proposerDisplayName(displayName)
                        .canManage(topicAuthorizer.canManageInCommunity(e.getCommunityId(), userId)).build());
    }

    @Transactional(readOnly = true)
    public Page<Topic> list(TopicListQuery query, Pageable pageable) {

        var organizationId = query.organizationId();
        var filter = new TopicFilter(query.kind(), query.status());
        var page = topicRepository.findFiltered(organizationId, query.communityId(), filter, pageable);
        return page(page, organizationId);
    }

    @Transactional(readOnly = true)
    public Page<Topic> listPast(String organizationId, Long communityId, String q, Pageable pageable) {

        return page(topicRepository.findPast(organizationId, communityId, q, pageable), organizationId);
    }

    @Transactional(readOnly = true)
    public Page<Topic> page(Page<TopicEntity> page, String organizationId) {

        var userId = currentUser.userId();
        var content = page.getContent();
        var ids = content.stream().map(TopicEntity::getId).toList();

        var scheduledIds = content.stream()
                .filter(e -> e.getStatus() == TopicStatus.SCHEDULED || e.getStatus() == TopicStatus.DONE)
                .map(TopicEntity::getId).toList();
        // findSchedules orders rows preferred-first per topic, so the first row seen is the one to keep.
        var schedules = new HashMap<Long, ScheduledFor>();
        for (var s : topicRepository.findSchedules(organizationId, scheduledIds)) {
            schedules.putIfAbsent(s.topicId(), ScheduledFor.builder().sessionId(s.sessionId())
                    .scheduledAt(s.scheduledAt()).scheduleLabel(s.scheduleLabel()).build());
        }

        // One read of every vote on the page yields both per-topic counts and the current user's
        // voted set, replacing the previous per-row count queries (an N+1 over the page).
        var votes = topicVoteRepository.findByOrganizationIdAndTopicIdIn(organizationId, ids);
        var voteCounts = votes.stream()
                .collect(Collectors.groupingBy(TopicVoteEntity::getTopicId, Collectors.counting()));
        Set<Long> votedIds = votes.stream().filter(v -> userId.equals(v.getUserId())).map(TopicVoteEntity::getTopicId)
                .collect(Collectors.toSet());

        var personIds = content.stream().map(TopicEntity::getProposerUserId).filter(Objects::nonNull).distinct()
                .toList();
        var displayNames = userProfileAccess.displayNames(organizationId, personIds);

        // Policy and role are constant within a community, so resolve manage-rights once per distinct
        // community rather than once per row.
        var canManageByCommunity = content.stream().map(TopicEntity::getCommunityId).distinct()
                .collect(Collectors.toMap(communityId -> communityId,
                        communityId -> topicAuthorizer.canManageInCommunity(communityId, userId)));

        return page.map(e -> toTopic(e,
                TopicDecoration.builder().scheduledFor(schedules.get(e.getId()))
                        .voteCount(voteCounts.getOrDefault(e.getId(), 0L)).hasVoted(votedIds.contains(e.getId()))
                        .proposerDisplayName(displayNames.getOrDefault(e.getProposerUserId(), e.getProposerUserId()))
                        .canManage(canManageByCommunity.get(e.getCommunityId())).build()));
    }

    /** Per-topic read-model context computed around the entity: schedule, votes, proposer label, manage rights. */
    @Builder
    private record TopicDecoration(ScheduledFor scheduledFor, long voteCount, boolean hasVoted,
            String proposerDisplayName, boolean canManage) {
    }

    // A topic can never be closed or reopened: only an action item's own lifecycle (ActionItemReadModel) has those
    // actions. Both flags are always false here, kept on the DTO so a caller need not special-case their absence.
    private Topic toTopic(TopicEntity e, TopicDecoration decoration) {

        return Topic.builder().id(e.getId()).communityId(e.getCommunityId()).kind(e.getKind()).title(e.getTitle())
                .description(e.getDescription()).tags(topicMapper.tags(e)).proposerUserId(e.getProposerUserId())
                .proposerDisplayName(decoration.proposerDisplayName()).canClose(false).canReopen(false)
                .voteCount(decoration.voteCount()).hasVoted(decoration.hasVoted()).canEdit(decoration.canManage())
                .canDelete(decoration.canManage() && e.getStatus() == TopicStatus.PROPOSED).status(e.getStatus())
                .scheduledFor(decoration.scheduledFor()).createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }
}
