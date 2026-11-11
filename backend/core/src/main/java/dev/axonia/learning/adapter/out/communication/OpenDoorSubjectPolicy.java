package dev.axonia.learning.adapter.out.communication;

import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.spi.MessageSubjectPolicy;
import dev.axonia.learning.application.port.out.CommunityAccess;
import lombok.RequiredArgsConstructor;

/**
 * The open-to-questions notice's answers to the delivery gate. Nothing switches it off: it is an in-app entry only, and
 * the panel is a complete record. A learner who left the community the notice is about is owed no entry. The subject is
 * {@code <communityId>:<area>:<declarerId>}, as {@link OpenDoorNotices} writes it.
 */
@Component
@RequiredArgsConstructor
class OpenDoorSubjectPolicy implements MessageSubjectPolicy {

    private final CommunityAccess communityAccess;

    @Override
    public Set<MessageType> handles() {

        return Set.of(OpenDoorNotices.TYPE);
    }

    @Override
    public boolean isEnabledFor(String organizationId, MessageType type, String subjectRef) {

        return true;
    }

    @Override
    public boolean isStillAMember(String organizationId, String subjectRef, String userId) {

        var communityId = Long.parseLong(subjectRef.substring(0, subjectRef.indexOf(':')));
        return communityAccess.isMember(organizationId, communityId, userId);
    }
}
