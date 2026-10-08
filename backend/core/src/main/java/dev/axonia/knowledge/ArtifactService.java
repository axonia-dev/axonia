package dev.axonia.knowledge;

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

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.TagNormalizer;
import dev.axonia.knowledge.internal.ArtifactAuthorizer;
import dev.axonia.knowledge.internal.ArtifactEntity;
import dev.axonia.knowledge.internal.ArtifactMapper;
import dev.axonia.knowledge.internal.ArtifactRepository;
import dev.axonia.knowledge.internal.ArtifactTagRef;
import dev.axonia.knowledge.internal.ArtifactTypeRepository;
import dev.axonia.knowledge.internal.AttachmentEntity;
import dev.axonia.knowledge.internal.AttachmentRepository;
import dev.axonia.knowledge.internal.ContributorResolver;
import dev.axonia.knowledge.internal.ResourceAttachAuthorizer;
import dev.axonia.knowledge.internal.SessionProvenanceGuard;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArtifactService {

    /** The width of {@code artifact_tag.tag}. Anything longer is dropped, never stored, never a failed insert. */
    private static final int MAX_TAG_LENGTH = 50;

    private final ArtifactRepository artifactRepository;
    private final ArtifactTypeRepository artifactTypeRepository;
    private final ArtifactMapper artifactMapper;
    private final ArtifactAuthorizer artifactAuthorizer;
    private final CommunityAccess communityAccess;
    private final ContributorResolver contributorResolver;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final AttachmentRepository attachmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionProvenanceGuard sessionProvenanceGuard;
    private final ResourceAttachAuthorizer resourceAttachAuthorizer;

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
        var entity = ArtifactEntity.builder().organizationId(organizationId).communityId(communityId)
                .typeId(request.typeId()).title(request.title().strip()).description(description)
                .url(request.url().strip()).contributorUserId(contributorUserId).status(ArtifactStatus.ACTIVE.name())
                .tags(refs(tags)).searchDocument(searchDocument(request.title(), description, tags));
        applyProvenance(entity, request.provenance());
        var saved = artifactRepository.save(entity.build());
        // The caller attached it; the contributor did the work. Conflating the two is the whole bug.
        autoAttach(new AutoAttachContext(saved, provenance, organizationId, userId, communityId, session));
        eventPublisher.publishEvent(ArtifactEvents.contributed(saved));
        return artifactMapper.toArtifact(saved);
    }

    @Transactional(readOnly = true)
    public Artifact get(Long id) {

        return artifactMapper
                .toArtifact(ArtifactLookup.findOrThrow(artifactRepository, organizationContext.organizationId(), id));
    }

    @Transactional(readOnly = true)
    public Page<Artifact> search(String slug, ArtifactFilter filter, Pageable pageable) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        var page = artifactRepository.search(organizationId, communityId, filter, pageable);
        return new PageImpl<>(artifactMapper.toArtifacts(page.getContent()), pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<ArtifactSession> sessions(String slug) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        return artifactRepository.sessionsWithArtifacts(organizationId, communityId);
    }

    @Transactional(readOnly = true)
    public LibraryStats stats(String slug) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        return artifactRepository.stats(organizationId, communityId);
    }

    @Transactional
    public Artifact edit(Long id, UpdateArtifactRequest request) {

        var organizationId = organizationContext.organizationId();
        var entity = ArtifactLookup.findOrThrow(artifactRepository, organizationId, id);
        artifactAuthorizer.requireContributorOrOrganizer(entity.getCommunityId(), entity.getContributorUserId(),
                currentUser.userId());
        if (entity.isArchived()) {
            throw new ConflictException("ARTIFACT_ARCHIVED", "Cannot edit an archived artifact");
        }
        requireType(organizationId, request.typeId());

        var tags = normalize(request.tags());
        var description = text(request.description());
        var saved = artifactRepository.save(entity.toBuilder().title(request.title().strip()).description(description)
                .url(request.url().strip()).typeId(request.typeId()).tags(refs(tags))
                .searchDocument(searchDocument(request.title(), description, tags)).build());
        eventPublisher.publishEvent(ArtifactEvents.contributed(saved));
        return artifactMapper.toArtifact(saved);
    }

    @Transactional
    public void remove(Long id) {

        var organizationId = organizationContext.organizationId();
        var entity = ArtifactLookup.findOrThrow(artifactRepository, organizationId, id);
        artifactAuthorizer.requireContributorOrOrganizer(entity.getCommunityId(), entity.getContributorUserId(),
                currentUser.userId());
        artifactRepository.delete(entity);
        eventPublisher.publishEvent(new ArtifactWithdrawn(entity.getId(), entity.getCommunityId()));
    }

    @Transactional
    public Artifact archive(Long id) {

        var organizationId = organizationContext.organizationId();
        var entity = ArtifactLookup.findOrThrow(artifactRepository, organizationId, id);
        requireOrganizer(entity.getCommunityId());
        if (!entity.isActive()) {
            throw new ConflictException("ARTIFACT_ARCHIVED", "Artifact is already archived");
        }
        var savedEntity = artifactRepository.save(entity.toBuilder().status(ArtifactStatus.ARCHIVED.name()).build());
        eventPublisher.publishEvent(new ArtifactWithdrawn(savedEntity.getId(), savedEntity.getCommunityId()));
        return artifactMapper.toArtifact(savedEntity);
    }

    @Transactional
    public Artifact restore(Long id) {

        var organizationId = organizationContext.organizationId();
        var entity = ArtifactLookup.findOrThrow(artifactRepository, organizationId, id);
        requireOrganizer(entity.getCommunityId());
        if (!entity.isArchived()) {
            throw new ConflictException("ARTIFACT_NOT_ARCHIVED", "Artifact is not archived");
        }
        var savedEntity = artifactRepository.save(entity.toBuilder().status(ArtifactStatus.ACTIVE.name()).build());
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
    private record AutoAttachContext(ArtifactEntity artifact, ProvenanceRequest provenance, String organizationId,
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
        attachmentRepository.save(AttachmentEntity.builder().organizationId(context.organizationId())
                .artifactId(context.artifact().getId()).sessionId(provenance.sessionId())
                .segmentId(provenance.segmentId()).attachedByUserId(context.userId()).build());
    }

    private void applyProvenance(ArtifactEntity.ArtifactEntityBuilder builder, ProvenanceRequest provenance) {

        if (provenance == null || provenance.sessionId() == null) {
            return;
        }
        builder.provenanceSessionId(provenance.sessionId()).provenanceSegmentId(provenance.segmentId())
                .provenanceLabel(provenance.label());
    }

    private void requireType(String organizationId, Long typeId) {

        artifactTypeRepository.findByOrganizationIdAndId(organizationId, typeId)
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

    private Set<ArtifactTagRef> refs(Set<String> tags) {

        return tags.stream().map(t -> ArtifactTagRef.builder().tag(t).build())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String searchDocument(String title, String description, Set<String> tags) {

        return (title + " " + description + " " + String.join(" ", tags)).strip();
    }

    private String text(String value) {

        return value == null ? "" : value;
    }
}
