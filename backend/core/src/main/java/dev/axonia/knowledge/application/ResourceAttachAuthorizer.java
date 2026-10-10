package dev.axonia.knowledge.application;

import org.springframework.stereotype.Component;

import dev.axonia.knowledge.application.port.out.CommunityAccess;
import dev.axonia.knowledge.domain.SessionRef;
import dev.axonia.shared.api.error.ForbiddenException;
import lombok.RequiredArgsConstructor;

/**
 * The floor for attaching an artifact to a session: schedule-management rights before the session is held, and open to
 * any community member once it is held. The caller must already be a known member of the community by the time this
 * runs - this only adds the schedule-management gate on top of that.
 *
 * <p>
 * Its own component rather than a private method on either caller because there are two writers of the same
 * {@code artifact_attachment} row - {@code ResourceService.attach} and {@code ArtifactService}'s auto-attach step on
 * capture. One check here means both enforce it, rather than one enforcing it and the other writing the same row
 * unguarded.
 */
@Component
@RequiredArgsConstructor
public class ResourceAttachAuthorizer {

    private final CommunityAccess communityAccess;
    private final SessionProvenanceGuard sessionProvenanceGuard;

    public void requireScheduleManagerUnlessHeld(long communityId, String userId, SessionRef session) {

        if (sessionProvenanceGuard.isHeld(session)) {
            return;
        }
        if (communityAccess.isOrganizer(communityId, userId)) {
            return;
        }
        if (!communityAccess.contributionPolicy(communityId).canManageSchedule()) {
            throw new ForbiddenException("Schedule-management rights required before the session is held");
        }
    }
}
