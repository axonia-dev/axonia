package dev.axonia.knowledge;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.knowledge.internal.ArtifactAuthorizer;
import dev.axonia.knowledge.internal.ArtifactEntity;
import dev.axonia.knowledge.internal.ArtifactMapper;
import dev.axonia.knowledge.internal.ArtifactRepository;
import dev.axonia.knowledge.internal.AttachmentEntity;
import dev.axonia.knowledge.internal.AttachmentRepository;
import dev.axonia.knowledge.internal.ResourceAttachAuthorizer;
import dev.axonia.knowledge.internal.SessionProvenanceGuard;
import dev.axonia.knowledge.internal.SessionResourceRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private static final String ATTACHMENT_FORBIDDEN_MESSAGE = "Only whoever attached it or an organizer may remove this attachment";
    private static final String ORIGIN_FORBIDDEN_MESSAGE = "Only the contributor or an organizer may remove this resource";

    private final AttachmentRepository attachmentRepository;
    private final SessionResourceRepository sessionResourceRepository;
    private final ArtifactRepository artifactRepository;
    private final ArtifactMapper artifactMapper;
    private final ArtifactAuthorizer artifactAuthorizer;
    private final CommunityAccess communityAccess;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final SessionProvenanceGuard sessionProvenanceGuard;
    private final ResourceAttachAuthorizer resourceAttachAuthorizer;

    @Transactional(readOnly = true)
    public List<SessionResource> resourcesForSession(String slug, Long sessionId) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        sessionProvenanceGuard.requireSessionInCommunity(sessionId, communityId);
        return sessionResourceRepository.findForSession(organizationId, sessionId, communityId).stream().map(row -> {
            var artifact = artifactMapper
                    .toArtifact(ArtifactLookup.findOrThrow(artifactRepository, organizationId, row.artifactId()));
            return SessionResource.builder().attachmentId(row.attachmentId()).artifact(artifact)
                    .segmentId(row.segmentId()).attachedByUserId(row.attachedByUserId()).build();
        }).toList();
    }

    @Transactional
    public Attachment attach(String slug, Long sessionId, AttachResourceRequest request) {

        var organizationId = organizationContext.organizationId();
        var userId = currentUser.userId();
        var communityId = communityAccess.communityIdForSlug(slug);
        if (!communityAccess.isMember(communityId, userId)) {
            throw new ForbiddenException("Community membership required");
        }
        var artifact = ArtifactLookup.findOrThrow(artifactRepository, organizationId, request.artifactId());
        if (!artifact.getCommunityId().equals(communityId)) {
            throw new ForbiddenException("Artifact belongs to another community");
        }
        if (artifact.isArchived()) {
            throw new ConflictException("ARTIFACT_ARCHIVED", "Cannot attach an archived artifact");
        }
        var session = sessionProvenanceGuard.requireSessionInCommunity(sessionId, communityId);
        resourceAttachAuthorizer.requireScheduleManagerUnlessHeld(communityId, userId, session);
        sessionProvenanceGuard.requireSegmentInSession(request.segmentId(), sessionId);
        var existing = attachmentRepository.findByOrganizationIdAndArtifactIdAndSessionIdAndSegmentId(organizationId,
                request.artifactId(), sessionId, request.segmentId());
        var entity = existing.orElseGet(() -> attachmentRepository
                .save(AttachmentEntity.builder().organizationId(organizationId).artifactId(request.artifactId())
                        .sessionId(sessionId).segmentId(request.segmentId()).attachedByUserId(userId).build()));
        return toAttachment(entity);
    }

    /**
     * Takes an artifact off a talk, or off the session when {@code segmentId} is null. An attachment present must be
     * removable by its own right - the attacher or an organizer - before anything is written; an origin naming this
     * session is then cleared only for the contributor or an organizer. An origin-only row is the one naming this
     * session and {@code segmentId}, and requires that same contributor-or-organizer right directly.
     */
    @Transactional
    public void detach(String slug, Long sessionId, Long artifactId, Long segmentId) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        sessionProvenanceGuard.requireSessionInCommunity(sessionId, communityId);
        var artifact = ArtifactLookup.findOrThrow(artifactRepository, organizationId, artifactId);
        if (!artifact.getCommunityId().equals(communityId)) {
            throw new NotFoundException("Artifact not found: " + artifactId);
        }
        var attachment = attachmentRepository.findByOrganizationIdAndArtifactIdAndSessionIdAndSegmentId(organizationId,
                artifactId, sessionId, segmentId);
        var cameFromThisSession = sessionId.equals(artifact.getProvenanceSessionId());
        var isTheOriginRow = cameFromThisSession && Objects.equals(segmentId, artifact.getProvenanceSegmentId());
        if (attachment.isEmpty() && !isTheOriginRow) {
            throw new NotFoundException("Artifact is not on this session: " + artifactId);
        }
        var actor = artifactAuthorizer.actor(communityId, currentUser.userId());
        if (attachment.isPresent()) {
            requireMayRemove(actor, attachment.get().getAttachedByUserId(), ATTACHMENT_FORBIDDEN_MESSAGE);
            attachmentRepository.delete(attachment.get());
            if (cameFromThisSession && actor.isOwnerOrOrganizer(artifact.getContributorUserId())) {
                clearOrigin(artifact);
            }
        } else {
            requireMayRemove(actor, artifact.getContributorUserId(), ORIGIN_FORBIDDEN_MESSAGE);
            clearOrigin(artifact);
        }
    }

    private static void requireMayRemove(ArtifactAuthorizer.Actor actor, String referenceUserId, String message) {

        if (!actor.isOwnerOrOrganizer(referenceUserId)) {
            throw new ForbiddenException(message);
        }
    }

    private void clearOrigin(ArtifactEntity artifact) {

        artifactRepository.save(
                artifact.toBuilder().provenanceSessionId(null).provenanceSegmentId(null).provenanceLabel(null).build());
    }

    private Attachment toAttachment(AttachmentEntity e) {

        return Attachment.builder().id(e.getId()).artifactId(e.getArtifactId()).sessionId(e.getSessionId())
                .segmentId(e.getSegmentId()).attachedByUserId(e.getAttachedByUserId()).createdAt(e.getCreatedAt())
                .build();
    }
}
