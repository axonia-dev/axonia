package dev.axonia.learning.adapter.out.communication;

import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.spi.MessageSubjectPolicy;
import dev.axonia.learning.application.port.out.CommunityAccess;
import dev.axonia.learning.application.port.out.OpenPeople;
import dev.axonia.learning.domain.OpenDoorSubject;
import lombok.RequiredArgsConstructor;

/**
 * The open-to-questions notice's answers to the delivery gate. It is judged again when it is delivered, not only when
 * it was sent: a declarer who removed the area, hid it, opted out or paused since is no longer open on it, and the
 * notice would reveal what those controls hide. A learner who left the community the notice is about is owed no entry.
 */
@Component
@RequiredArgsConstructor
class OpenDoorSubjectPolicy implements MessageSubjectPolicy {

    private final CommunityAccess communityAccess;
    private final OpenPeople openPeople;

    @Override
    public Set<MessageType> handles() {

        return Set.of(OpenDoorSubject.MESSAGE_TYPE);
    }

    @Override
    public boolean isEnabledFor(String organizationId, MessageType type, String subjectRef) {

        var subject = OpenDoorSubject.parse(subjectRef);
        return openPeople.isOpen(organizationId, subject.communityId(), subject.area(), subject.declarerId());
    }

    @Override
    public boolean isStillAMember(String organizationId, String subjectRef, String userId) {

        return communityAccess.isMember(organizationId, OpenDoorSubject.parse(subjectRef).communityId(), userId);
    }
}
