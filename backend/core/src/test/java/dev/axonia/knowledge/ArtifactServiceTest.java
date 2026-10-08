package dev.axonia.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.ValidationException;
import dev.axonia.community.ContributionPolicy;
import dev.axonia.knowledge.internal.ArtifactAuthorizer;
import dev.axonia.knowledge.internal.ArtifactEntity;
import dev.axonia.knowledge.internal.ArtifactMapper;
import dev.axonia.knowledge.internal.ArtifactRepository;
import dev.axonia.knowledge.internal.ArtifactTagRef;
import dev.axonia.knowledge.internal.ArtifactTypeEntity;
import dev.axonia.knowledge.internal.ArtifactTypeRepository;
import dev.axonia.knowledge.internal.ResourceAttachAuthorizer;
import dev.axonia.knowledge.internal.SessionProvenanceGuard;
import dev.axonia.knowledge.mother.ArtifactEntityMother;
import dev.axonia.knowledge.mother.ArtifactMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class ArtifactServiceTest {

    @Mock
    private ArtifactRepository artifactRepository;
    @Mock
    private ArtifactTypeRepository artifactTypeRepository;
    @Mock
    private ArtifactMapper artifactMapper;
    @Mock
    private ArtifactAuthorizer artifactAuthorizer;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private dev.axonia.knowledge.internal.AttachmentRepository attachmentRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SessionAccess sessionAccess;

    private ArtifactService sut;

    @BeforeEach
    void setUp() {

        // The guard and the authorizer are real, over the mocked ports: the checks under test are the ones that ship.
        var sessionProvenanceGuard = new SessionProvenanceGuard(sessionAccess);
        var resourceAttachAuthorizer = new ResourceAttachAuthorizer(communityAccess, sessionProvenanceGuard);
        sut = new ArtifactService(artifactRepository, artifactTypeRepository, artifactMapper, artifactAuthorizer,
                communityAccess, new dev.axonia.knowledge.internal.ContributorResolver(communityAccess), currentUser,
                organizationContext, attachmentRepository, eventPublisher, sessionProvenanceGuard,
                resourceAttachAuthorizer);
        when(organizationContext.organizationId()).thenReturn("default");
    }

    // -------------------------------------------------------------------------
    // capture
    // -------------------------------------------------------------------------

    @Test
    void capture_notMember_throws() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(false);

        // then
        assertThatThrownBy(() -> sut.capture("eng",
                new CreateArtifactRequest("Title", 1L, "https://example.com", null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(artifactRepository, never()).save(any());
    }

    @Test
    void capture_unknownType_throws() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> sut.capture("eng",
                new CreateArtifactRequest("Title", 99L, "https://example.com", null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void capture_success_normalizesTagsAndBuildsSearchDocument() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(ArtifactTypeEntity.builder().id(1L).label("Recording").build()));
        when(artifactRepository.save(any()))
                .thenAnswer(inv -> ((ArtifactEntity) inv.getArgument(0)).toBuilder().id(10L).build());
        var artifact = ArtifactMother.any().id(10L).build();
        when(artifactMapper.toArtifact(any())).thenReturn(artifact);

        var request = new CreateArtifactRequest("  My Talk  ", 1L, "  https://example.com/talk  ", "A description",
                Set.of("  Java  ", "JAVA", "spring"), null, null);

        // when
        var result = sut.capture("eng", request);

        // then
        assertThat(result).isEqualTo(artifact);

        var captor = ArgumentCaptor.forClass(ArtifactEntity.class);
        verify(artifactRepository).save(captor.capture());
        var saved = captor.getValue();

        assertThat(saved.getTitle()).isEqualTo("My Talk");
        assertThat(saved.getUrl()).isEqualTo("https://example.com/talk");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getContributorUserId()).isEqualTo("alice");
        assertThat(saved.getTags()).extracting(ArtifactTagRef::getTag).containsOnly("java", "spring");
        assertThat(saved.getSearchDocument()).contains("My Talk").contains("A description").contains("java");
        assertThat(saved.getProvenanceSessionId()).isNull();
    }

    @Test
    void capture_withProvenance_storesProvenance() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(ArtifactTypeEntity.builder().id(1L).label("Recording").build()));
        when(artifactRepository.save(any()))
                .thenAnswer(inv -> ((ArtifactEntity) inv.getArgument(0)).toBuilder().id(10L).build());
        when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(10L).build());
        when(sessionAccess.session(42L)).thenReturn(new SessionRef(42L, 1L, true));
        when(sessionAccess.sessionIdOfSlot(7L)).thenReturn(Optional.of(42L));

        var provenance = new ProvenanceRequest(42L, 7L, "Session recording");
        var request = new CreateArtifactRequest("Title", 1L, "https://example.com", "desc", Set.of("tag"), provenance,
                null);

        // when
        sut.capture("eng", request);

        // then
        var captor = ArgumentCaptor.forClass(ArtifactEntity.class);
        verify(artifactRepository).save(captor.capture());
        var saved = captor.getValue();

        assertThat(saved.getProvenanceSessionId()).isEqualTo(42L);
        assertThat(saved.getProvenanceSegmentId()).isEqualTo(7L);
        assertThat(saved.getProvenanceLabel()).isEqualTo("Session recording");
    }

    @Test
    void capture_withNullTagsAndNullProvenance_handlesGracefully() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(ArtifactTypeEntity.builder().id(1L).label("Recording").build()));
        when(artifactRepository.save(any()))
                .thenAnswer(inv -> ((ArtifactEntity) inv.getArgument(0)).toBuilder().id(10L).build());
        when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(10L).build());

        // null tags, null provenance, null description
        var request = new CreateArtifactRequest("Title", 1L, "https://example.com", null, null, null, null);

        // when
        sut.capture("eng", request);

        // then
        var captor = ArgumentCaptor.forClass(ArtifactEntity.class);
        verify(artifactRepository).save(captor.capture());
        var saved = captor.getValue();

        assertThat(saved.getTags()).isEmpty();
        assertThat(saved.getProvenanceSessionId()).isNull();
        assertThat(saved.getDescription()).isEqualTo("");
    }

    @Test
    void capture_withProvenanceNullSessionId_skipsProvenance() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(ArtifactTypeEntity.builder().id(1L).label("Recording").build()));
        when(artifactRepository.save(any()))
                .thenAnswer(inv -> ((ArtifactEntity) inv.getArgument(0)).toBuilder().id(10L).build());
        when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(10L).build());

        // provenance object exists but sessionId is null
        var provenance = new ProvenanceRequest(null, null, null);
        var request = new CreateArtifactRequest("Title", 1L, "https://example.com", "desc", null, provenance, null);

        // when
        sut.capture("eng", request);

        // then
        var captor = ArgumentCaptor.forClass(ArtifactEntity.class);
        verify(artifactRepository).save(captor.capture());
        assertThat(captor.getValue().getProvenanceSessionId()).isNull();
    }

    @Test
    void capture_tagsWithNullAndBlankEntries_filtered() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(ArtifactTypeEntity.builder().id(1L).label("Recording").build()));
        when(artifactRepository.save(any()))
                .thenAnswer(inv -> ((ArtifactEntity) inv.getArgument(0)).toBuilder().id(10L).build());
        when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(10L).build());

        // include null entry and blank entry to exercise filter branches
        var tags = new java.util.HashSet<String>();
        tags.add(null);
        tags.add("   ");
        tags.add("java");
        var request = new CreateArtifactRequest("Title", 1L, "https://example.com", "desc", tags, null, null);

        // when
        sut.capture("eng", request);

        // then
        var captor = ArgumentCaptor.forClass(ArtifactEntity.class);
        verify(artifactRepository).save(captor.capture());
        assertThat(captor.getValue().getTags()).extracting(ArtifactTagRef::getTag).containsExactly("java");
    }

    @Test
    void capture_tagLongerThanTheColumn_isDroppedRatherThanFailingTheWrite() {

        // given - a Confluence label carried in by a migration, longer than artifact_tag.tag is wide
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(ArtifactTypeEntity.builder().id(1L).label("Recording").build()));
        when(artifactRepository.save(any()))
                .thenAnswer(inv -> ((ArtifactEntity) inv.getArgument(0)).toBuilder().id(10L).build());
        when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(10L).build());

        var tags = new java.util.LinkedHashSet<String>();
        tags.add("x".repeat(51));
        tags.add("x".repeat(50));
        tags.add("java");
        var request = new CreateArtifactRequest("Title", 1L, "https://example.com", "desc", tags, null, null);

        // when
        sut.capture("eng", request);

        // then - the over-long label is dropped, exactly as events drops it, and the contribution still lands
        var captor = ArgumentCaptor.forClass(ArtifactEntity.class);
        verify(artifactRepository).save(captor.capture());
        assertThat(captor.getValue().getTags()).extracting(ArtifactTagRef::getTag)
                .containsExactlyInAnyOrder("x".repeat(50), "java");
    }

    /**
     * Not held, but the caller carries the community's {@code canManageSchedule} policy - the other way through
     * {@link dev.axonia.knowledge.internal.ResourceAttachAuthorizer}'s gate besides being an organizer, proven here at
     * the {@code ArtifactService} call site rather than only in {@code ResourceAttachAuthorizerTest}'s isolation.
     */
    @Test
    void capture_withSessionAndSegment_createsAttachment() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(ArtifactTypeEntity.builder().id(1L).label("Slides").build()));
        when(artifactRepository.save(any()))
                .thenAnswer(inv -> ((ArtifactEntity) inv.getArgument(0)).toBuilder().id(10L).build());
        when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(10L).build());
        when(sessionAccess.session(42L)).thenReturn(new SessionRef(42L, 1L, false));
        when(sessionAccess.sessionIdOfSlot(7L)).thenReturn(Optional.of(42L));
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, false, true));

        var provenance = new ProvenanceRequest(42L, 7L, "Talk - alice");

        // when
        sut.capture("eng", new CreateArtifactRequest("Title", 1L, "https://x.dev/a", null, null, provenance, null));

        // then
        var captor = org.mockito.ArgumentCaptor.forClass(dev.axonia.knowledge.internal.AttachmentEntity.class);
        verify(attachmentRepository).save(captor.capture());
        var link = captor.getValue();
        assertThat(link.getArtifactId()).isEqualTo(10L);
        assertThat(link.getSessionId()).isEqualTo(42L);
        assertThat(link.getSegmentId()).isEqualTo(7L);
        assertThat(link.getAttachedByUserId()).isEqualTo("alice");
    }

    /**
     * The same {@code artifact_attachment} row {@code ResourceService.attach} refuses to write for a plain member
     * before a session is held, capture's own auto-attach step must refuse too - it is a second writer of the same row,
     * not a different one. Provenance naming a not-yet-held session's slot is exactly what a member submitting "I
     * captured this from today's slides, ahead of the session" would send.
     *
     * <p>
     * {@code capture} is {@code @Transactional} and {@link ForbiddenException} is unchecked, so the artifact row this
     * method already saved is rolled back too, not left orphaned - a Mockito unit test cannot observe that rollback
     * directly (there is no real transaction manager here), but it can pin that nothing downstream of the refusal runs:
     * {@code eventPublisher} never sees the event capture publishes once it actually completes.
     */
    @Test
    void captureRefusesAutoAttachBeforeHeldForAPlainMember() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(ArtifactTypeEntity.builder().id(1L).label("Slides").build()));
        when(artifactRepository.save(any()))
                .thenAnswer(inv -> ((ArtifactEntity) inv.getArgument(0)).toBuilder().id(10L).build());
        when(sessionAccess.session(42L)).thenReturn(new SessionRef(42L, 1L, false));
        when(sessionAccess.sessionIdOfSlot(7L)).thenReturn(Optional.of(42L));
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, false, false));

        var provenance = new ProvenanceRequest(42L, 7L, "Talk - alice");
        var request = new CreateArtifactRequest("Title", 1L, "https://x.dev/a", null, null, provenance, null);

        // when / then
        assertThatThrownBy(() -> sut.capture("eng", request)).isInstanceOf(ForbiddenException.class);
        verify(attachmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void capture_withSessionButNoSegment_doesNotAttach() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(ArtifactTypeEntity.builder().id(1L).label("Slides").build()));
        when(artifactRepository.save(any()))
                .thenAnswer(inv -> ((ArtifactEntity) inv.getArgument(0)).toBuilder().id(10L).build());
        when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(10L).build());
        when(sessionAccess.session(42L)).thenReturn(new SessionRef(42L, 1L, false));

        var provenance = new ProvenanceRequest(42L, null, "Talk");

        // when
        sut.capture("eng", new CreateArtifactRequest("Title", 1L, "https://x.dev/a", null, null, provenance, null));

        // then
        verify(attachmentRepository, never()).save(any());
    }

    /**
     * Provenance is a claim about where an artifact came from, and it is rendered on the session it names: in that
     * session's resource list and in its wrap-up mail. A member of one community naming another community's session
     * plants a link on a page they cannot otherwise write to. Session ids are sequential and visible across the
     * Organization by design, so the id is not the secret and the check has to be the check.
     *
     * <p>
     * Before {@code SessionProvenanceGuard} was wired in, this exact input - a member of "eng" naming session 42, which
     * belongs to community 2 - SUCCEEDED: the artifact and the attachment were both written. It is refused now.
     */
    @Test
    void captureRefusesProvenanceNamingAnotherCommunitysSession() {

        // given - session 42 belongs to community 2, the caller is only a member of community 1 ("eng").
        // The type/save/mapper stubs below are what let the OLD code (no guard) run capture() to completion and
        // record the rows instead of throwing; the fix throws before any of them are reached, which is why they are
        // stubbed leniently rather than with when(...) - Mockito's strict stubbing would otherwise fail this test on
        // the very fix it verifies.
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(sessionAccess.session(42L)).thenReturn(new SessionRef(42L, 2L, false));
        lenient().when(artifactTypeRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(ArtifactTypeEntity.builder().id(1L).label("Recording").build()));
        lenient().when(artifactRepository.save(any()))
                .thenAnswer(inv -> ((ArtifactEntity) inv.getArgument(0)).toBuilder().id(10L).build());
        lenient().when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(10L).build());

        var provenance = new ProvenanceRequest(42L, null, "Talk");
        var request = new CreateArtifactRequest("Title", 1L, "https://example.com", null, null, provenance, null);

        // then
        assertThatThrownBy(() -> sut.capture("eng", request)).isInstanceOf(NotFoundException.class);
        verify(artifactRepository, never()).save(any());
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void captureRefusesASegmentThatBelongsToADifferentSession() {

        // given - session 100 is this community's own, but segment 55 belongs to a different session. See the
        // lenient() note on captureRefusesProvenanceNamingAnotherCommunitysSession above.
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, false));
        when(sessionAccess.sessionIdOfSlot(55L)).thenReturn(Optional.of(999L));
        lenient().when(artifactTypeRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(ArtifactTypeEntity.builder().id(1L).label("Recording").build()));
        lenient().when(artifactRepository.save(any()))
                .thenAnswer(inv -> ((ArtifactEntity) inv.getArgument(0)).toBuilder().id(10L).build());
        lenient().when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(10L).build());

        var provenance = new ProvenanceRequest(100L, 55L, "Talk");
        var request = new CreateArtifactRequest("Title", 1L, "https://example.com", null, null, provenance, null);

        // then
        assertThatThrownBy(() -> sut.capture("eng", request)).isInstanceOf(ValidationException.class);
        verify(artifactRepository, never()).save(any());
        verify(attachmentRepository, never()).save(any());
    }

    /**
     * A segment names a session; a provenance naming a segment with no session at all is meaningless on its own terms,
     * a defect the cross-community check happens to catch as a side effect rather than the thing it exists to catch.
     * {@code ProvenanceRequest} carries no bean validation of its own, so nothing rejected this shape before
     * {@code requireSegmentInSession} was wired into {@code capture}: the old code silently accepted it, recorded no
     * provenance at all (both {@code applyProvenance} and {@code autoAttach} bail out on a null session id), and
     * returned 201. It is refused now.
     */
    @Test
    void captureRefusesASegmentNamedWithoutItsSession() {

        // given - a provenance naming a segment but no session at all
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(sessionAccess.sessionIdOfSlot(7L)).thenReturn(Optional.of(5L));

        var provenance = new ProvenanceRequest(null, 7L, "Talk");
        var request = new CreateArtifactRequest("Title", 1L, "https://example.com", null, null, provenance, null);

        // then
        assertThatThrownBy(() -> sut.capture("eng", request)).isInstanceOf(ValidationException.class);
        verify(artifactRepository, never()).save(any());
        verify(attachmentRepository, never()).save(any());
    }

    /** Provenance naming this community's own session is the ordinary path and still works. */
    @Test
    void captureAcceptsProvenanceNamingThisCommunitysOwnSession() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, false));
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(ArtifactTypeEntity.builder().id(1L).label("Recording").build()));
        when(artifactRepository.save(any()))
                .thenAnswer(inv -> ((ArtifactEntity) inv.getArgument(0)).toBuilder().id(10L).build());
        var artifact = ArtifactMother.any().id(10L)
                .provenance(Provenance.builder().sessionId(100L).label("Talk").build()).build();
        when(artifactMapper.toArtifact(any())).thenReturn(artifact);

        var provenance = new ProvenanceRequest(100L, null, "Talk");
        var request = new CreateArtifactRequest("Title", 1L, "https://example.com", null, null, provenance, null);

        // when
        var result = sut.capture("eng", request);

        // then
        assertThat(result.provenance()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // get
    // -------------------------------------------------------------------------

    @Test
    void get_success() {

        // given
        var entity = ArtifactEntityMother.any().id(5L).build();
        when(artifactRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(entity));
        var artifact = ArtifactMother.any().id(5L).build();
        when(artifactMapper.toArtifact(entity)).thenReturn(artifact);

        // when
        var result = sut.get(5L);

        // then
        assertThat(result).isEqualTo(artifact);
    }

    @Test
    void get_notFound_throws() {

        // given
        when(artifactRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> sut.get(5L)).isInstanceOf(NotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // search
    // -------------------------------------------------------------------------

    @Test
    void search_delegatesToRepositoryAndMaps() {

        // given
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        var entity = ArtifactEntityMother.any().id(3L).build();
        var artifact = ArtifactMother.any().id(3L).build();
        var filter = ArtifactFilter.builder().build();
        Page<ArtifactEntity> page = new PageImpl<>(List.of(entity));
        when(artifactRepository.search("default", 1L, filter, Pageable.unpaged())).thenReturn(page);
        when(artifactMapper.toArtifacts(List.of(entity))).thenReturn(List.of(artifact));

        // when
        var result = sut.search("eng", filter, Pageable.unpaged());

        // then
        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent()).containsExactly(artifact);
    }

    @Test
    void sessions_delegatesToRepository() {

        // given
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        var sessions = List.of(ArtifactSession.builder().sessionId(100L).count(3L).build());
        when(artifactRepository.sessionsWithArtifacts("default", 1L)).thenReturn(sessions);

        // when
        var result = sut.sessions("eng");

        // then
        assertThat(result).isEqualTo(sessions);
    }

    @Test
    void stats_delegatesToRepository() {

        // given
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        var stats = LibraryStats.builder().total(10L).sessionCount(4L).capturedThisMonth(2L).build();
        when(artifactRepository.stats("default", 1L)).thenReturn(stats);

        // when
        var result = sut.stats("eng");

        // then
        assertThat(result).isEqualTo(stats);
    }

    // -------------------------------------------------------------------------
    // edit
    // -------------------------------------------------------------------------

    @Test
    void edit_authorizerDenial_propagates() {

        // given
        var entity = ArtifactEntityMother.any().id(1L).build();
        when(artifactRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("eve");
        doThrow(new ForbiddenException("denied")).when(artifactAuthorizer)
                .requireContributorOrOrganizer(entity.getCommunityId(), entity.getContributorUserId(), "eve");

        // then
        assertThatThrownBy(() -> sut.edit(1L, new UpdateArtifactRequest("T", 1L, "https://x.com", null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void edit_archivedArtifact_throws() {

        // given
        var entity = ArtifactEntityMother.any().id(1L).status("ARCHIVED").build();
        when(artifactRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("dev-user");

        // then
        assertThatThrownBy(() -> sut.edit(1L, new UpdateArtifactRequest("T", 1L, "https://x.com", null, null)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("ARTIFACT_ARCHIVED");
    }

    @Test
    void edit_unknownType_throws() {

        // given
        var entity = ArtifactEntityMother.any().id(1L).build();
        when(artifactRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("dev-user");
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> sut.edit(1L, new UpdateArtifactRequest("T", 99L, "https://x.com", null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void edit_success_updatesFields() {

        // given
        var entity = ArtifactEntityMother.any().id(1L).build();
        when(artifactRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("dev-user");
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 2L))
                .thenReturn(Optional.of(ArtifactTypeEntity.builder().id(2L).label("Article").build()));
        when(artifactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var artifact = ArtifactMother.any().id(1L).build();
        when(artifactMapper.toArtifact(any())).thenReturn(artifact);

        // when
        var result = sut.edit(1L,
                new UpdateArtifactRequest("  New Title  ", 2L, "  https://new.com  ", "new desc", Set.of("kotlin")));

        // then
        assertThat(result).isEqualTo(artifact);

        var captor = ArgumentCaptor.forClass(ArtifactEntity.class);
        verify(artifactRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("New Title");
        assertThat(saved.getUrl()).isEqualTo("https://new.com");
        assertThat(saved.getTypeId()).isEqualTo(2L);
        assertThat(saved.getTags()).extracting(ArtifactTagRef::getTag).containsExactly("kotlin");
    }

    // -------------------------------------------------------------------------
    // remove
    // -------------------------------------------------------------------------

    @Test
    void remove_success_deletes() {

        // given
        var entity = ArtifactEntityMother.any().id(1L).build();
        when(artifactRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("dev-user");

        // when
        sut.remove(1L);

        // then
        verify(artifactRepository).delete(entity);
    }

    @Test
    void remove_authorizerDenial_propagates() {

        // given
        var entity = ArtifactEntityMother.any().id(1L).build();
        when(artifactRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("eve");
        doThrow(new ForbiddenException("denied")).when(artifactAuthorizer)
                .requireContributorOrOrganizer(entity.getCommunityId(), entity.getContributorUserId(), "eve");

        // then
        assertThatThrownBy(() -> sut.remove(1L)).isInstanceOf(ForbiddenException.class);
        verify(artifactRepository, never()).delete(any());
    }

    @Test
    void remove_publishesArtifactWithdrawn() {

        // given
        var entity = ArtifactEntityMother.any().id(6L).communityId(25L).build();
        when(artifactRepository.findByOrganizationIdAndId("default", 6L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("contributor");

        // when
        sut.remove(6L);

        // then
        var captor = ArgumentCaptor.forClass(ArtifactWithdrawn.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var event = captor.getValue();
        assertThat(event.artifactId()).isEqualTo(6L);
        assertThat(event.communityId()).isEqualTo(25L);
    }

    // -------------------------------------------------------------------------
    // archive
    // -------------------------------------------------------------------------

    @Test
    void archive_success_setsArchived() {

        // given
        var entity = ArtifactEntityMother.any().id(1L).status("ACTIVE").build();
        when(artifactRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("organizer");
        when(communityAccess.isOrganizer(1L, "organizer")).thenReturn(true);
        when(artifactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(1L).build());

        // when
        sut.archive(1L);

        // then
        var captor = ArgumentCaptor.forClass(ArtifactEntity.class);
        verify(artifactRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    void archive_notOrganizer_throws() {

        // given
        var entity = ArtifactEntityMother.any().id(1L).status("ACTIVE").build();
        when(artifactRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);

        // then
        assertThatThrownBy(() -> sut.archive(1L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void archive_alreadyArchived_throws() {

        // given
        var entity = ArtifactEntityMother.any().id(1L).status("ARCHIVED").build();
        when(artifactRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("organizer");
        when(communityAccess.isOrganizer(1L, "organizer")).thenReturn(true);

        // then
        assertThatThrownBy(() -> sut.archive(1L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("ARTIFACT_ARCHIVED");
    }

    // -------------------------------------------------------------------------
    // restore
    // -------------------------------------------------------------------------

    @Test
    void restore_success_setsActive() {

        // given
        var entity = ArtifactEntityMother.any().id(1L).status("ARCHIVED").build();
        when(artifactRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("organizer");
        when(communityAccess.isOrganizer(1L, "organizer")).thenReturn(true);
        when(artifactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(1L).build());

        // when
        sut.restore(1L);

        // then
        var captor = ArgumentCaptor.forClass(ArtifactEntity.class);
        verify(artifactRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void restore_notArchived_throws() {

        // given
        var entity = ArtifactEntityMother.any().id(1L).status("ACTIVE").build();
        when(artifactRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("organizer");
        when(communityAccess.isOrganizer(1L, "organizer")).thenReturn(true);

        // then
        assertThatThrownBy(() -> sut.restore(1L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("ARTIFACT_NOT_ARCHIVED");
    }

    @Test
    void restore_notOrganizer_throws() {

        // given
        var entity = ArtifactEntityMother.any().id(1L).status("ARCHIVED").build();
        when(artifactRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);

        // then
        assertThatThrownBy(() -> sut.restore(1L)).isInstanceOf(ForbiddenException.class);
    }

    // -------------------------------------------------------------------------
    // event publishing
    // -------------------------------------------------------------------------

    @Test
    void capture_publishesArtifactContributed() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(42L);
        when(communityAccess.isMember(42L, "alice")).thenReturn(true);
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional
                .of(dev.axonia.knowledge.internal.ArtifactTypeEntity.builder().id(1L).label("Recording").build()));
        when(artifactRepository.save(any()))
                .thenAnswer(inv -> ((ArtifactEntity) inv.getArgument(0)).toBuilder().id(99L).build());
        when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(99L).build());

        var request = new CreateArtifactRequest("Title", 1L, "https://example.com", "desc", Set.of("java", "spring"),
                null, null);

        // when
        sut.capture("eng", request);

        // then
        var captor = ArgumentCaptor.forClass(ArtifactContributed.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var event = captor.getValue();
        assertThat(event.artifactId()).isEqualTo(99L);
        assertThat(event.communityId()).isEqualTo(42L);
        assertThat(event.contributorUserId()).isEqualTo("alice");
        assertThat(event.tags()).containsExactlyInAnyOrder("java", "spring");
    }

    @Test
    void edit_publishesArtifactContributed() {

        // given
        var entity = ArtifactEntityMother.any().id(7L).communityId(10L).contributorUserId("bob").build();
        when(artifactRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("bob");
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional
                .of(dev.axonia.knowledge.internal.ArtifactTypeEntity.builder().id(1L).label("Article").build()));
        when(artifactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(7L).build());

        // when
        sut.edit(7L, new UpdateArtifactRequest("New Title", 1L, "https://new.com", "desc", Set.of("kotlin")));

        // then
        var captor = ArgumentCaptor.forClass(ArtifactContributed.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var event = captor.getValue();
        assertThat(event.artifactId()).isEqualTo(7L);
        assertThat(event.communityId()).isEqualTo(10L);
        assertThat(event.contributorUserId()).isEqualTo("bob");
        assertThat(event.tags()).containsExactly("kotlin");
    }

    @Test
    void archive_publishesArtifactWithdrawn() {

        // given
        var entity = ArtifactEntityMother.any().id(5L).communityId(20L).status("ACTIVE").build();
        when(artifactRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("organizer");
        when(communityAccess.isOrganizer(20L, "organizer")).thenReturn(true);
        when(artifactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(5L).build());

        // when
        sut.archive(5L);

        // then
        var captor = ArgumentCaptor.forClass(ArtifactWithdrawn.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var event = captor.getValue();
        assertThat(event.artifactId()).isEqualTo(5L);
        assertThat(event.communityId()).isEqualTo(20L);
    }

    @Test
    void restore_republishesArtifactContributed() {

        // given
        var entity = ArtifactEntityMother.any().id(3L).communityId(30L).contributorUserId("carol").status("ARCHIVED")
                .tags(Set.of(dev.axonia.knowledge.internal.ArtifactTagRef.builder().tag("ddd").build())).build();
        when(artifactRepository.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("organizer");
        when(communityAccess.isOrganizer(30L, "organizer")).thenReturn(true);
        when(artifactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(3L).build());

        // when
        sut.restore(3L);

        // then
        var captor = ArgumentCaptor.forClass(ArtifactContributed.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var event = captor.getValue();
        assertThat(event.artifactId()).isEqualTo(3L);
        assertThat(event.communityId()).isEqualTo(30L);
        assertThat(event.contributorUserId()).isEqualTo("carol");
        assertThat(event.tags()).containsExactly("ddd");
    }
}
