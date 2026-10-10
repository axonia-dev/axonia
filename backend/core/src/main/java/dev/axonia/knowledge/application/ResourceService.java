package dev.axonia.knowledge.application;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.knowledge.api.AttachResourceRequest;
import dev.axonia.knowledge.api.Attachment;
import dev.axonia.knowledge.api.Resources;
import dev.axonia.knowledge.api.SessionResource;
import dev.axonia.knowledge.application.port.out.ArtifactStore;
import dev.axonia.knowledge.application.port.out.AttachmentStore;
import dev.axonia.knowledge.application.port.out.CommunityAccess;
import dev.axonia.knowledge.application.port.out.CurrentUser;
import dev.axonia.knowledge.application.port.out.OrganizationContext;
import dev.axonia.knowledge.application.port.out.SessionResourceRepository;
import dev.axonia.knowledge.domain.StoredArtifact;
import dev.axonia.knowledge.domain.StoredAttachment;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResourceService implements Resources {

    private static final String ATTACHMENT_FORBIDDEN_MESSAGE = "Only whoever attached it or an organizer may remove this attachment";
    private static final String ORIGIN_FORBIDDEN_MESSAGE = "Only the contributor or an organizer may remove this resource";

    private final AttachmentStore attachmentStore;
    private final SessionResourceRepository sessionResourceRepository;
    private final ArtifactStore artifactStore;
    private final ArtifactMapper artifactMapper;
    private final ArtifactAuthorizer artifactAuthorizer;
    private final CommunityAccess communityAccess;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final SessionProvenanceGuard sessionProvenanceGuard;
    private final ResourceAttachAuthorizer resourceAttachAuthorizer;

    @Override
    @Transactional(readOnly = true)
    public List<SessionResource> resourcesForSession(String slug, Long sessionId) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        sessionProvenanceGuard.requireSessionInCommunity(sessionId, communityId);
        return sessionResourceRepository.findForSession(organizationId, sessionId, communityId).stream().map(row -> {
            var artifact = artifactMapper
                    .toArtifact(ArtifactLookup.findOrThrow(artifactStore, organizationId, row.artifactId()));
            return SessionResource.builder().attachmentId(row.attachmentId()).artifact(artifact)
                    .segmentId(row.segmentId()).attachedByUserId(row.attachedByUserId()).build();
        }).toList();
    }

    @Override
    @Transactional
    public Attachment attach(String slug, Long sessionId, AttachResourceRequest request) {

        var organizationId = organizationContext.organizationId();
        var userId = currentUser.userId();
        var communityId = communityAccess.communityIdForSlug(slug);
        if (!communityAccess.isMember(communityId, userId)) {
            throw new ForbiddenException("Community membership required");
        }
        var artifact = ArtifactLookup.findOrThrow(artifactStore, organizationId, request.artifactId());
        if (!artifact.communityId().equals(communityId)) {
            throw new ForbiddenException("Artifact belongs to another community");
        }
        if (artifact.isArchived()) {
            throw new ConflictException("ARTIFACT_ARCHIVED", "Cannot attach an archived artifact");
        }
        var session = sessionProvenanceGuard.requireSessionInCommunity(sessionId, communityId);
        resourceAttachAuthorizer.requireScheduleManagerUnlessHeld(communityId, userId, session);
        sessionProvenanceGuard.requireSegmentInSession(request.segmentId(), sessionId);
        var existing = attachmentStore.find(organizationId, request.artifactId(), sessionId, request.segmentId());
        var entity = existing.orElseGet(() -> attachmentStore
                .save(StoredAttachment.builder().organizationId(organizationId).artifactId(request.artifactId())
                        .sessionId(sessionId).segmentId(request.segmentId()).attachedByUserId(userId).build()));
        return toAttachment(entity);
    }

    /**
     * Takes an artifact off a talk, or off the session when {@code segmentId} is null. An attachment present must be
     * removable by its own right - the attacher or an organizer - before anything is written; an origin naming this
     * session is then cleared only for the contributor or an organizer. An origin-only row is the one naming this
     * session and {@code segmentId}, and requires that same contributor-or-organizer right directly.
     */
    @Override
    @Transactional
    public void detach(String slug, Long sessionId, Long artifactId, Long segmentId) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        sessionProvenanceGuard.requireSessionInCommunity(sessionId, communityId);
        var artifact = ArtifactLookup.findOrThrow(artifactStore, organizationId, artifactId);
        if (!artifact.communityId().equals(communityId)) {
            throw new NotFoundException("Artifact not found: " + artifactId);
        }
        var attachment = attachmentStore.find(organizationId, artifactId, sessionId, segmentId);
        var cameFromThisSession = sessionId.equals(artifact.provenanceSessionId());
        var isTheOriginRow = cameFromThisSession && Objects.equals(segmentId, artifact.provenanceSegmentId());
        if (attachment.isEmpty() && !isTheOriginRow) {
            throw new NotFoundException("Artifact is not on this session: " + artifactId);
        }
        var actor = artifactAuthorizer.actor(communityId, currentUser.userId());
        if (attachment.isPresent()) {
            requireMayRemove(actor, attachment.get().attachedByUserId(), ATTACHMENT_FORBIDDEN_MESSAGE);
            attachmentStore.delete(attachment.get());
            if (cameFromThisSession && actor.isOwnerOrOrganizer(artifact.contributorUserId())) {
                clearOrigin(artifact);
            }
        } else {
            requireMayRemove(actor, artifact.contributorUserId(), ORIGIN_FORBIDDEN_MESSAGE);
            clearOrigin(artifact);
        }
    }

    private static void requireMayRemove(ArtifactAuthorizer.Actor actor, String referenceUserId, String message) {

        if (!actor.isOwnerOrOrganizer(referenceUserId)) {
            throw new ForbiddenException(message);
        }
    }

    private void clearOrigin(StoredArtifact artifact) {

        artifactStore.save(
                artifact.toBuilder().provenanceSessionId(null).provenanceSegmentId(null).provenanceLabel(null).build());
    }

    private Attachment toAttachment(StoredAttachment e) {

        return Attachment.builder().id(e.id()).artifactId(e.artifactId()).sessionId(e.sessionId())
                .segmentId(e.segmentId()).attachedByUserId(e.attachedByUserId()).createdAt(e.createdAt()).build();
    }
}
