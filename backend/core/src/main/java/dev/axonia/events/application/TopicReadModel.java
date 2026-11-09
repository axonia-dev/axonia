package dev.axonia.events.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.ScheduledFor;
import dev.axonia.events.api.Topic;
import dev.axonia.events.api.TopicKind;
import dev.axonia.events.api.TopicStatus;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.TopicStore;
import dev.axonia.events.application.port.out.TopicVoteStore;
import dev.axonia.events.application.port.out.UserProfileAccess;
import dev.axonia.events.domain.TopicRecord;
import dev.axonia.events.domain.TopicVoteRecord;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

/** Turns persisted topics into the {@link Topic} read model: schedule, vote counts, display names, manage rights. */
@Component
@RequiredArgsConstructor
public class TopicReadModel {

    private final TopicVoteStore topicVoteStore;
    private final UserProfileAccess userProfileAccess;
    private final TopicAuthorizer topicAuthorizer;
    private final TopicMapper topicMapper;
    private final CurrentUser currentUser;
    private final TopicStore topicStore;

    @Transactional(readOnly = true)
    public Topic single(TopicRecord e, ScheduledFor scheduledFor) {

        var orgId = e.getOrganizationId();
        var userId = currentUser.userId();
        var voteCount = topicVoteStore.countByOrganizationIdAndTopicId(orgId, e.getId());
        var hasVoted = topicVoteStore.existsByOrganizationIdAndTopicIdAndUserId(orgId, e.getId(), userId);
        var displayName = userProfileAccess.displayName(orgId, e.getProposerUserId());
        var speakers = speakersOf(orgId, List.of(e)).get(e.getId());
        return toTopic(e,
                TopicDecoration.builder().scheduledFor(scheduledFor).voteCount(voteCount).hasVoted(hasVoted)
                        .proposerDisplayName(displayName).speakerUserIds(speakers)
                        .canManageSpeakers(topicAuthorizer.canManageSpeakers(e, speakers, userId))
                        .canManage(topicAuthorizer.canManageInCommunity(e.getCommunityId(), userId)).build());
    }

    @Transactional(readOnly = true)
    public Page<Topic> list(TopicListQuery query, Pageable pageable) {

        var organizationId = query.organizationId();
        var filter = new TopicFilter(query.kind(), query.status());
        var page = topicStore.findFiltered(organizationId, query.communityId(), filter, pageable);
        return page(page, organizationId);
    }

    @Transactional(readOnly = true)
    public Page<Topic> listPast(String organizationId, Long communityId, String q, Pageable pageable) {

        return page(topicStore.findPast(organizationId, communityId, q, pageable), organizationId);
    }

    @Transactional(readOnly = true)
    public Page<Topic> page(Page<TopicRecord> page, String organizationId) {

        var userId = currentUser.userId();
        var content = page.getContent();
        var ids = content.stream().map(TopicRecord::getId).toList();

        var scheduledIds = content.stream()
                .filter(e -> e.getStatus() == TopicStatus.SCHEDULED || e.getStatus() == TopicStatus.DONE)
                .map(TopicRecord::getId).toList();
        // findSchedules orders rows preferred-first per topic, so the first row seen is the one to keep.
        var schedules = new HashMap<Long, ScheduledFor>();
        for (var s : topicStore.findSchedules(organizationId, scheduledIds)) {
            schedules.putIfAbsent(s.topicId(), ScheduledFor.builder().sessionId(s.sessionId())
                    .scheduledAt(s.scheduledAt()).scheduleLabel(s.scheduleLabel()).build());
        }

        // One read of every vote on the page yields both per-topic counts and the current user's
        // voted set, replacing the previous per-row count queries (an N+1 over the page).
        var votes = topicVoteStore.findByOrganizationIdAndTopicIdIn(organizationId, ids);
        var voteCounts = votes.stream()
                .collect(Collectors.groupingBy(TopicVoteRecord::getTopicId, Collectors.counting()));
        Set<Long> votedIds = votes.stream().filter(v -> userId.equals(v.getUserId())).map(TopicVoteRecord::getTopicId)
                .collect(Collectors.toSet());

        var speakers = speakersOf(organizationId, content);

        var personIds = content.stream().map(TopicRecord::getProposerUserId).filter(Objects::nonNull).distinct()
                .toList();
        var displayNames = userProfileAccess.displayNames(organizationId, personIds);

        // Policy and role are constant within a community, so resolve manage-rights once per distinct
        // community rather than once per row.
        var canManageByCommunity = content.stream().map(TopicRecord::getCommunityId).distinct()
                .collect(Collectors.toMap(communityId -> communityId,
                        communityId -> topicAuthorizer.canManageInCommunity(communityId, userId)));

        var speakerRightsByCommunity = content.stream().map(TopicRecord::getCommunityId).distinct()
                .collect(Collectors.toMap(communityId -> communityId,
                        communityId -> topicAuthorizer.speakerRightsIn(communityId, userId)));

        return page.map(e -> toTopic(e, TopicDecoration.builder().scheduledFor(schedules.get(e.getId()))
                .voteCount(voteCounts.getOrDefault(e.getId(), 0L)).hasVoted(votedIds.contains(e.getId()))
                .proposerDisplayName(displayNames.getOrDefault(e.getProposerUserId(), e.getProposerUserId()))
                .speakerUserIds(speakers.get(e.getId()))
                .canManageSpeakers(
                        speakerRightsByCommunity.get(e.getCommunityId()).canManage(e, speakers.get(e.getId()), userId))
                .canManage(canManageByCommunity.get(e.getCommunityId())).build()));
    }

    /**
     * Who presents each topic, read once: a backlog topic's speakers are its own loaded collection, and only the topics
     * that left the backlog ask the store, for their slots' presenters.
     */
    Map<Long, List<String>> speakersOf(String organizationId, List<TopicRecord> topics) {

        var scheduledIds = topics.stream().filter(t -> t.getStatus() != TopicStatus.PROPOSED).map(TopicRecord::getId)
                .toList();
        var fromSlots = scheduledIds.isEmpty()
                ? Map.<Long, List<String>>of()
                : topicStore.findSpeakers(organizationId, scheduledIds);
        return topics.stream()
                .collect(Collectors.toMap(TopicRecord::getId,
                        t -> t.getStatus() == TopicStatus.PROPOSED
                                ? t.speakerUserIds()
                                : fromSlots.getOrDefault(t.getId(), List.of())));
    }

    /**
     * Per-topic read-model context computed around the entity: schedule, votes, proposer label, speakers, manage
     * rights.
     */
    @Builder
    private record TopicDecoration(ScheduledFor scheduledFor, long voteCount, boolean hasVoted,
            String proposerDisplayName, List<String> speakerUserIds, boolean canManageSpeakers, boolean canManage) {
    }

    // A topic can never be closed or reopened: only an action item's own lifecycle (ActionItemReadModel) has those
    // actions. Both flags are always false here, kept on the DTO so a caller need not special-case their absence.
    private Topic toTopic(TopicRecord e, TopicDecoration decoration) {

        return Topic.builder().id(e.getId()).communityId(e.getCommunityId())
                .kind(TopicKind.forSpeakers(decoration.speakerUserIds())).title(e.getTitle())
                .description(e.getDescription()).tags(topicMapper.tags(e)).proposerUserId(e.getProposerUserId())
                .proposerDisplayName(decoration.proposerDisplayName()).speakerUserIds(decoration.speakerUserIds())
                .canManageSpeakers(decoration.canManageSpeakers()).canClose(false).canReopen(false)
                .voteCount(decoration.voteCount()).hasVoted(decoration.hasVoted()).canEdit(decoration.canManage())
                .canDelete(decoration.canManage() && e.getStatus() == TopicStatus.PROPOSED).status(e.getStatus())
                .scheduledFor(decoration.scheduledFor()).createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }
}
