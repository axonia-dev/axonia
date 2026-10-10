package dev.axonia.knowledge.application;

import org.springframework.stereotype.Component;

import dev.axonia.knowledge.application.port.out.CommunityAccess;
import dev.axonia.shared.api.error.ForbiddenException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ArtifactAuthorizer {

    private static final String FORBIDDEN_MESSAGE = "Only the contributor or an organizer may modify this artifact";

    private final CommunityAccess communityAccess;

    /** One acting user in one community, for a request that checks them against several owners. */
    public Actor actor(long communityId, String actingUserId) {

        return new Actor(communityId, actingUserId);
    }

    public void requireContributorOrOrganizer(long communityId, String contributorUserId, String actingUserId) {

        if (!actor(communityId, actingUserId).isOwnerOrOrganizer(contributorUserId)) {
            throw new ForbiddenException(FORBIDDEN_MESSAGE);
        }
    }

    /** Reads the acting user's organizer role at most once, however many owners it is checked against. */
    public final class Actor {

        private final long communityId;
        private final String actingUserId;
        private boolean organizerRead;
        private boolean organizer;

        private Actor(long communityId, String actingUserId) {

            this.communityId = communityId;
            this.actingUserId = actingUserId;
        }

        public boolean isOwnerOrOrganizer(String ownerUserId) {

            return ownerUserId.equals(actingUserId) || isOrganizer();
        }

        private boolean isOrganizer() {

            if (!organizerRead) {
                organizer = communityAccess.isOrganizer(communityId, actingUserId);
                organizerRead = true;
            }
            return organizer;
        }
    }
}
