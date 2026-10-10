package dev.axonia.events.application;

import org.springframework.stereotype.Component;

import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.TopicStore;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SlotRecord;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SessionAuthorizer {

    private final CommunityAccess communityAccess;
    private final TopicStore topicStore;

    public void requireOrganizer(SessionRecord session, String userId) {

        if (!communityAccess.isOrganizer(session.getCommunityId(), userId)) {
            throw new ForbiddenException("Only an organizer may do this");
        }
    }

    /**
     * The floor for everything about a session's own outgoing mail: previewing the reminder or the wrap-up, moving one,
     * or suppressing it. An organizer, or a member of a community that has opened its {@code canManageCommunication}
     * axis - the same right that governs whether this community sends those messages at all.
     */
    public void requireCommunicationManager(SessionRecord session, String userId) {

        if (!communityAccess.canManageCommunication(session.getCommunityId(), userId)) {
            throw new ForbiddenException("Managing this community's communication is required");
        }
    }

    /**
     * Gates a {@code /communities/{slug}/sessions/{id}/...} route: {@code communityId}, resolved from that slug, must
     * be the session's own community. A {@code NotFoundException}, not a {@code ForbiddenException} - the same 404 an
     * unknown slug or an unknown session id already produces - so a slug naming a real but different community reveals
     * nothing about whether a session id exists anywhere else, exactly the not-found behaviour every other lookup on
     * this entity already has.
     */
    public void requireCommunityMatches(SessionRecord session, long communityId) {

        if (!session.getCommunityId().equals(communityId)) {
            throw new NotFoundException("Session not found: " + session.getId());
        }
    }

    /** A slot's owner (presenter or the topic's proposer), an organizer, or a member with canManageSchedule. */
    public void requireSlotOwnerOrManager(String organizationId, SessionRecord session, SlotRecord slot,
            String userId) {

        if (isPresenter(slot, userId) || isProposer(organizationId, slot, userId)) {
            return;
        }
        var communityId = session.getCommunityId();
        if (communityAccess.isOrganizer(communityId, userId)) {
            return;
        }
        if (communityAccess.contributionPolicy(communityId).canManageSchedule()
                && communityAccess.isMember(communityId, userId)) {
            return;
        }
        throw new ForbiddenException("Editing the schedule requires slot ownership or schedule-management rights");
    }

    /**
     * Organizer, or a member when the community's canManageSchedule axis is open. The same floor
     * {@link #requireSlotOwnerOrManager} bottoms out on, usable before a SlotRecord exists yet (e.g. recording a
     * brand-new slot).
     */
    public void requireScheduleManager(long communityId, String userId) {

        if (communityAccess.isOrganizer(communityId, userId)) {
            return;
        }
        if (communityAccess.contributionPolicy(communityId).canManageSchedule()
                && communityAccess.isMember(communityId, userId)) {
            return;
        }
        throw new ForbiddenException("Recording a slot requires schedule-management rights");
    }

    public void requireStructureEditor(SessionRecord session, String userId) {

        var communityId = session.getCommunityId();
        if (communityAccess.isOrganizer(communityId, userId)) {
            return;
        }
        if (communityAccess.contributionPolicy(communityId).canEditStructure()
                && communityAccess.isMember(communityId, userId)) {
            return;
        }
        throw new ForbiddenException("Structure editing requires organizer or contributor rights");
    }

    private boolean isPresenter(SlotRecord slot, String userId) {

        return slot.getPresenters() != null
                && slot.getPresenters().stream().anyMatch(p -> p.getUserId().equals(userId));
    }

    private boolean isProposer(String organizationId, SlotRecord slot, String userId) {

        var topicId = slot.getTopicId();
        if (topicId == null) {
            return false;
        }
        return topicStore.findByOrganizationIdAndId(organizationId, topicId)
                .map(t -> userId.equals(t.getProposerUserId())).orElse(false);
    }
}
