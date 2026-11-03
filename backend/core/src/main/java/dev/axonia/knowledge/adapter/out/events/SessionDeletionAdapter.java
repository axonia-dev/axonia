package dev.axonia.knowledge.adapter.out.events;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.spi.SessionDeletionParticipant;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactRepository;
import dev.axonia.knowledge.adapter.out.persistence.AttachmentRepository;
import lombok.RequiredArgsConstructor;

/**
 * A deleted session never happened, so nothing in the library may point at it any more. The artifacts themselves stay:
 * they were contributed to the community, not to the session.
 */
@Component("knowledgeSessionDeletionAdapter")
@RequiredArgsConstructor
class SessionDeletionAdapter implements SessionDeletionParticipant {

    private final AttachmentRepository attachmentRepository;
    private final ArtifactRepository artifactRepository;

    @Override
    @Transactional
    public void sessionDeleted(String organizationId, long communityId, long sessionId) {

        attachmentRepository.deleteByOrganizationIdAndSessionId(organizationId, sessionId);
        artifactRepository.clearSessionProvenance(organizationId, communityId, sessionId);
    }
}
