package dev.axonia.knowledge.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.TagNormalizer;
import dev.axonia.knowledge.api.Artifact;
import dev.axonia.knowledge.api.ArtifactFilter;
import dev.axonia.knowledge.api.ArtifactStatus;
import dev.axonia.knowledge.api.ArtifactWithdrawn;
import dev.axonia.knowledge.api.Artifacts;
import dev.axonia.knowledge.api.CreateArtifactRequest;
import dev.axonia.knowledge.api.ProvenanceRequest;
import dev.axonia.knowledge.application.port.out.ArtifactStore;
import dev.axonia.knowledge.application.port.out.ArtifactTypeStore;
import dev.axonia.knowledge.application.port.out.AttachmentStore;
import dev.axonia.knowledge.application.port.out.CommunityAccess;
import dev.axonia.knowledge.application.port.out.CurrentUser;
import dev.axonia.knowledge.application.port.out.OrganizationContext;
import dev.axonia.knowledge.domain.ArtifactSession;
import dev.axonia.knowledge.domain.LibraryStats;
import dev.axonia.knowledge.domain.SessionRef;
import dev.axonia.knowledge.domain.StoredArtifact;
import dev.axonia.knowledge.domain.StoredAttachment;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArtifactService implements Artifacts {

    /** The width of {@code artifact_tag.tag}. Anything longer is dropped, never stored, never a failed insert. */
    private static final int MAX_TAG_LENGTH = 50;

    private final ArtifactStore artifactStore;
    private final ArtifactTypeStore artifactTypeStore;
    private final ArtifactMapper artifactMapper;
    private final ArtifactAuthorizer artifactAuthorizer;
    private final CommunityAccess communityAccess;
    private final ContributorResolver contributorResolver;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final AttachmentStore attachmentStore;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionProvenanceGuard sessionProvenanceGuard;
    private final ResourceAttachAuthorizer resourceAttachAuthorizer;

    @Override
    @Transactional
    public Artifact capture(String slug, CreateArtifactRequest request) {

        var organizationId = organizationContext.organizationId();
        var userId = currentUser.userId();
        var communityId = communityAccess.communityIdForSlug(slug);
        if (!communityAccess.isMember(communityId, userId)) {
            throw new ForbiddenException("Community membership required");
        }
        var provenance = request.provenance();
        SessionRef session = null;
        if (provenance != null) {
            session = sessionProvenanceGuard.requireSessionInCommunity(provenance.sessionId(), communityId);
            sessionProvenanceGuard.requireSegmentInSession(provenance.segmentId(), provenance.sessionId());
        }
        var contributorUserId = contributorResolver.resolve(communityId, userId, request.contributor());
        requireType(organizationId, request.typeId());

        var tags = normalize(request.tags());
        var description = text(request.description());
        var entity = StoredArtifact.builder().organizationId(organizationId).communityId(communityId)
                .typeId(request.typeId()).title(request.title().strip()).description(description)
                .url(request.url().strip()).contributorUserId(contributorUserId).status(ArtifactStatus.ACTIVE.name())
                .tags(tags).searchDocument(searchDocument(request.title(), description, tags));
        applyProvenance(entity, request.provenance());
        var saved = artifactStore.save(entity.build());
        // The caller attached it; the contributor did the work. Conflating the two is the whole bug.
        autoAttach(new AutoAttachContext(saved, provenance, organizationId, userId, communityId, session));
        eventPublisher.publishEvent(ArtifactEvents.contributed(saved));
        return artifactMapper.toArtifact(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Artifact get(Long id) {

        return artifactMapper
                .toArtifact(ArtifactLookup.findOrThrow(artifactStore, organizationContext.organizationId(), id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Artifact> search(String slug, ArtifactFilter filter, Pageable pageable) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        var page = artifactStore.search(organizationId, communityId, filter, pageable);
        return new PageImpl<>(artifactMapper.toArtifacts(page.getContent()), pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<ArtifactSession> sessions(String slug) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        return artifactStore.sessionsWithArtifacts(organizationId, communityId);
    }

    @Transactional(readOnly = true)
    public LibraryStats stats(String slug) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        return artifactStore.stats(organizationId, communityId);
    }

    @Transactional
    public Artifact edit(Long id, UpdateArtifactRequest request) {

        var organizationId = organizationContext.organizationId();
        var entity = ArtifactLookup.findOrThrow(artifactStore, organizationId, id);
        artifactAuthorizer.requireContributorOrOrganizer(entity.communityId(), entity.contributorUserId(),
                currentUser.userId());
        if (entity.isArchived()) {
            throw new ConflictException("ARTIFACT_ARCHIVED", "Cannot edit an archived artifact");
        }
        requireType(organizationId, request.typeId());

        var tags = normalize(request.tags());
        var description = text(request.description());
        var saved = artifactStore.save(entity.toBuilder().title(request.title().strip()).description(description)
                .url(request.url().strip()).typeId(request.typeId()).tags(tags)
                .searchDocument(searchDocument(request.title(), description, tags)).build());
        eventPublisher.publishEvent(ArtifactEvents.contributed(saved));
        return artifactMapper.toArtifact(saved);
    }

    @Transactional
    public void remove(Long id) {

        var organizationId = organizationContext.organizationId();
        var entity = ArtifactLookup.findOrThrow(artifactStore, organizationId, id);
        artifactAuthorizer.requireContributorOrOrganizer(entity.communityId(), entity.contributorUserId(),
                currentUser.userId());
        artifactStore.delete(entity);
        eventPublisher.publishEvent(new ArtifactWithdrawn(entity.id(), entity.communityId()));
    }

    @Transactional
    public Artifact archive(Long id) {

        var organizationId = organizationContext.organizationId();
        var entity = ArtifactLookup.findOrThrow(artifactStore, organizationId, id);
        requireOrganizer(entity.communityId());
        if (!entity.isActive()) {
            throw new ConflictException("ARTIFACT_ARCHIVED", "Artifact is already archived");
        }
        var savedEntity = artifactStore.save(entity.toBuilder().status(ArtifactStatus.ARCHIVED.name()).build());
        eventPublisher.publishEvent(new ArtifactWithdrawn(savedEntity.id(), savedEntity.communityId()));
        return artifactMapper.toArtifact(savedEntity);
    }

    @Transactional
    public Artifact restore(Long id) {

        var organizationId = organizationContext.organizationId();
        var entity = ArtifactLookup.findOrThrow(artifactStore, organizationId, id);
        requireOrganizer(entity.communityId());
        if (!entity.isArchived()) {
            throw new ConflictException("ARTIFACT_NOT_ARCHIVED", "Artifact is not archived");
        }
        var savedEntity = artifactStore.save(entity.toBuilder().status(ArtifactStatus.ACTIVE.name()).build());
        eventPublisher.publishEvent(ArtifactEvents.contributed(savedEntity));
        return artifactMapper.toArtifact(savedEntity);
    }

    private void requireOrganizer(long communityId) {

        if (!communityAccess.isOrganizer(communityId, currentUser.userId())) {
            throw new ForbiddenException("Organizer role required");
        }
    }

    /**
     * Groups {@link #autoAttach}'s six inputs under the parameter-count limit, the same way
     * {@code SlotService.TopicAttachment} groups {@code attachTopic}'s. {@code session} is the {@link SessionRef}
     * {@link SessionProvenanceGuard#requireSessionInCommunity} already fetched in {@link #capture}, {@code null} when
     * {@code provenance} carried no session - the same case {@link #autoAttach} returns on before ever reading it.
     */
    private record AutoAttachContext(StoredArtifact artifact, ProvenanceRequest provenance, String organizationId,
            String userId, long communityId, SessionRef session) {
    }

    /**
     * The caller attached it; the contributor did the work. Conflating the two is the whole bug - see
     * {@link #capture}'s own comment at its call site. Carries the same schedule-management floor
     * {@code ResourceService.attach} enforces ({@link ResourceAttachAuthorizer}): this writes the identical
     * {@code artifact_attachment} row through a second path, so a plain member naming an unheld session's slot as this
     * artifact's provenance must be refused here too, not only through the resource-attach endpoint.
     */
    private void autoAttach(AutoAttachContext context) {

        var provenance = context.provenance();
        if (provenance == null || provenance.sessionId() == null || provenance.segmentId() == null) {
            return;
        }
        resourceAttachAuthorizer.requireScheduleManagerUnlessHeld(context.communityId(), context.userId(),
                context.session());
        attachmentStore.save(StoredAttachment.builder().organizationId(context.organizationId())
                .artifactId(context.artifact().id()).sessionId(provenance.sessionId()).segmentId(provenance.segmentId())
                .attachedByUserId(context.userId()).build());
    }

    private void applyProvenance(StoredArtifact.StoredArtifactBuilder builder, ProvenanceRequest provenance) {

        if (provenance == null || provenance.sessionId() == null) {
            return;
        }
        builder.provenanceSessionId(provenance.sessionId()).provenanceSegmentId(provenance.segmentId())
                .provenanceLabel(provenance.label());
    }

    private void requireType(String organizationId, Long typeId) {

        artifactTypeStore.find(organizationId, typeId)
                .orElseThrow(() -> new NotFoundException("Artifact type not found: " + typeId));
    }

    /**
     * A tag is stored under the one name the whole product knows it by, {@link TagNormalizer}'s, so the vocabulary a
     * curator reads off the expertise projection names these very rows and a rename reaches them.
     *
     * <p>
     * The tag column is {@code varchar(50)}, so a longer tag is not a tag, it is a 500: a Confluence label carried in
     * by a migration used to reach the insert and come back as an unhandled DataIntegrityViolationException. Dropped,
     * exactly as the events context drops it, so dirty history loses one label instead of the whole write.
     */
    private Set<String> normalize(Set<String> raw) {

        if (raw == null) {
            return Set.of();
        }
        return raw.stream().filter(t -> t != null).map(TagNormalizer::normalize).filter(t -> !t.isBlank())
                .filter(t -> t.length() <= MAX_TAG_LENGTH).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String searchDocument(String title, String description, Set<String> tags) {

        return (title + " " + description + " " + String.join(" ", tags)).strip();
    }

    private String text(String value) {

        return value == null ? "" : value;
    }
}
