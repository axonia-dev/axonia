package dev.axonia.knowledge.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.knowledge.application.port.out.AttachmentStore;
import dev.axonia.knowledge.domain.StoredAttachment;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcAttachmentStore implements AttachmentStore {

    private final AttachmentRepository attachmentRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredAttachment> find(String organizationId, Long artifactId, Long sessionId, Long segmentId) {

        return attachmentRepository.findByOrganizationIdAndArtifactIdAndSessionIdAndSegmentId(organizationId,
                artifactId, sessionId, segmentId).map(JdbcAttachmentStore::toAttachment);
    }

    @Override
    @Transactional
    public StoredAttachment save(StoredAttachment attachment) {

        return toAttachment(attachmentRepository.save(toEntity(attachment)));
    }

    @Override
    public void delete(StoredAttachment attachment) {

        attachmentRepository.delete(toEntity(attachment));
    }

    static StoredAttachment toAttachment(AttachmentEntity entity) {

        return StoredAttachment.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .artifactId(entity.getArtifactId()).sessionId(entity.getSessionId()).segmentId(entity.getSegmentId())
                .attachedByUserId(entity.getAttachedByUserId()).createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static AttachmentEntity toEntity(StoredAttachment attachment) {

        return AttachmentEntity.builder().id(attachment.id()).organizationId(attachment.organizationId())
                .artifactId(attachment.artifactId()).sessionId(attachment.sessionId()).segmentId(attachment.segmentId())
                .attachedByUserId(attachment.attachedByUserId()).createdAt(attachment.createdAt())
                .updatedAt(attachment.updatedAt()).version(attachment.version()).build();
    }
}
