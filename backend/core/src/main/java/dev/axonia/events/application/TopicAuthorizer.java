package dev.axonia.events.application;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.events.api.TopicStatus;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.domain.TopicRecord;
import dev.axonia.shared.api.error.ForbiddenException;
import lombok.RequiredArgsConstructor;

/**
 * The one place a community's topic-management policy is turned into a yes/no answer, and the one place the
 * assignee-or-manager rule an action item's close/drop/reopen applies. Takes a bare {@code communityId} rather than a
 * {@code TopicRecord}: the policy is a property of the community, not of the topic, and the entity carried nothing else
 * this class ever used - which is exactly what let {@code ActionItemService} reuse it for an {@code ActionItemRecord}
 * instead of copying it.
 */
@Component
@RequiredArgsConstructor
public class TopicAuthorizer {

    private final CommunityAccess communityAccess;

    public void requireTopicManagementPermission(long communityId, String userId) {

        if (!canManageInCommunity(communityId, userId)) {
            throw new ForbiddenException("Insufficient permissions to manage this community's content");
        }
    }

    public boolean canManageInCommunity(long communityId, String userId) {

        var policy = communityAccess.topicManagementPolicy(communityId);
        return switch (policy) {
            case ORGANIZER_ONLY -> communityAccess.isOrganizer(communityId, userId);
            case ANY_MEMBER -> communityAccess.isMember(communityId, userId);
        };
    }

    /**
     * An assignee who is a member decides their own work without needing a manage right; everyone else needs one.
     * Short-circuits before the policy lookup when the caller is such an assignee.
     */
    public void requireAssigneeOrManager(long communityId, String assigneeUserId, String userId) {

        if (userId.equals(assigneeUserId) && communityAccess.isMember(communityId, userId)) {
            return;
        }
        requireTopicManagementPermission(communityId, userId);
    }

    /**
     * The pure form of {@link #requireAssigneeOrManager}, for a read model that has already resolved {@code canManage}
     * once - typically per community, batched across a whole page - rather than one that can afford to look it up
     * itself. Takes the resolved flag instead of a {@code communityId} so a batched caller never pays a second lookup
     * per row just to ask the same question this method would otherwise ask on its own.
     */
    public static boolean assigneeOrManager(boolean canManage, String assigneeUserId, String userId) {

        return canManage || userId.equals(assigneeUserId);
    }

    /**
     * Who may change a topic's speakers, with the same floor as a slot's presenters
     * ({@link SessionAuthorizer#requireSlotOwnerOrManager}): its creator or a speaker while a member, an organizer, or
     * a member when the community has opened {@code canManageSchedule}.
     */
    public boolean canManageSpeakers(TopicRecord topic, List<String> speakerUserIds, String userId) {

        return speakerRightsIn(topic.getCommunityId(), userId).canManage(topic, speakerUserIds, userId);
    }

    public void requireSpeakerManager(TopicRecord topic, String userId) {

        if (!canManageSpeakers(topic, topic.speakerUserIds(), userId)) {
            throw new ForbiddenException(
                    "Changing another person's place as a speaker requires being the topic's creator or a speaker, "
                            + "or schedule-management rights");
        }
    }

    /** The per-community half of {@link #canManageSpeakers}, resolved once for a whole page of one community. */
    public SpeakerRights speakerRightsIn(long communityId, String userId) {

        var member = communityAccess.isMember(communityId, userId);
        var managesSchedule = communityAccess.isOrganizer(communityId, userId)
                || member && communityAccess.contributionPolicy(communityId).canManageSchedule();
        return new SpeakerRights(member, managesSchedule);
    }

    public record SpeakerRights(boolean member, boolean managesSchedule) {

        /** Only a backlog topic takes speaker writes: once scheduled, who presents it is managed on its slot. */
        public boolean canManage(TopicRecord topic, List<String> speakerUserIds, String userId) {

            return topic.getStatus() == TopicStatus.PROPOSED && (managesSchedule
                    || member && (userId.equals(topic.getProposerUserId()) || speakerUserIds.contains(userId)));
        }
    }
}
