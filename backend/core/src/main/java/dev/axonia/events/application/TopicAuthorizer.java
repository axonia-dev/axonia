package dev.axonia.events.application;

import org.springframework.stereotype.Component;

import dev.axonia.events.application.port.out.CommunityAccess;
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
     * The assignee decides their own work without needing a manage right; everyone else needs one. Short-circuits
     * before consulting {@link CommunityAccess} at all when the caller is the assignee, so an assignee closing their
     * own item never pays a policy lookup for a check it cannot fail.
     */
    public void requireAssigneeOrManager(long communityId, String assigneeUserId, String userId) {

        if (userId.equals(assigneeUserId)) {
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
}
