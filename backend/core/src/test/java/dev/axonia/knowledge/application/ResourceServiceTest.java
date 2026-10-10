package dev.axonia.knowledge.application;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.api.ContributionPolicy;
import dev.axonia.knowledge.api.ArtifactMother;
import dev.axonia.knowledge.api.AttachResourceRequest;
import dev.axonia.knowledge.application.port.out.ArtifactStore;
import dev.axonia.knowledge.application.port.out.AttachmentStore;
import dev.axonia.knowledge.application.port.out.CommunityAccess;
import dev.axonia.knowledge.application.port.out.CurrentUser;
import dev.axonia.knowledge.application.port.out.OrganizationContext;
import dev.axonia.knowledge.application.port.out.SessionAccess;
import dev.axonia.knowledge.application.port.out.SessionResourceRepository;
import dev.axonia.knowledge.domain.SessionRef;
import dev.axonia.knowledge.domain.SessionResourceRow;
import dev.axonia.knowledge.domain.StoredArtifact;
import dev.axonia.knowledge.domain.StoredArtifactMother;
import dev.axonia.knowledge.domain.StoredAttachment;
import dev.axonia.knowledge.domain.StoredAttachmentMother;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class ResourceServiceTest {

    @Mock
    private AttachmentStore attachmentStore;
    @Mock
    private SessionResourceRepository sessionResourceRepository;
    @Mock
    private ArtifactStore artifactStore;
    @Mock
    private ArtifactMapper artifactMapper;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private SessionAccess sessionAccess;

    private ResourceService sut;

    @BeforeEach
    void setUp() {

        // The guard and the authorizers are real, over the mocked ports: the checks under test are the ones that ship.
        var sessionProvenanceGuard = new SessionProvenanceGuard(sessionAccess);
        var resourceAttachAuthorizer = new ResourceAttachAuthorizer(communityAccess, sessionProvenanceGuard);
        sut = new ResourceService(attachmentStore, sessionResourceRepository, artifactStore, artifactMapper,
                new ArtifactAuthorizer(communityAccess), communityAccess, currentUser, organizationContext,
                sessionProvenanceGuard, resourceAttachAuthorizer);
        when(organizationContext.organizationId()).thenReturn("default");
    }

    @Test
    void resourcesForSession_mapsRowsToArtifacts() {

        // given
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, false));
        when(sessionResourceRepository.findForSession("default", 100L, 1L))
                .thenReturn(List.of(new SessionResourceRow(5L, 9L, 7L, "alice")));
        var entity = StoredArtifactMother.any().id(9L).build();
        when(artifactStore.find("default", 9L)).thenReturn(Optional.of(entity));
        var artifact = ArtifactMother.any().id(9L).build();
        when(artifactMapper.toArtifact(entity)).thenReturn(artifact);

        // when
        var result = sut.resourcesForSession("eng", 100L);

        // then
        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.attachmentId()).isEqualTo(5L);
            assertThat(r.segmentId()).isEqualTo(7L);
            assertThat(r.attachedByUserId()).isEqualTo("alice");
            assertThat(r.artifact()).isEqualTo(artifact);
        });
    }

    @Test
    void attach_notMember_throws() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(false);

        // then
        assertThatThrownBy(() -> sut.attach("eng", 100L, new AttachResourceRequest(7L, 9L)))
                .isInstanceOf(ForbiddenException.class);
        verify(attachmentStore, never()).save(any());
    }

    @Test
    void attach_otherCommunity_throws() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactStore.find("default", 9L))
                .thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(2L).build()));

        // then
        assertThatThrownBy(() -> sut.attach("eng", 100L, new AttachResourceRequest(7L, 9L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void attach_archivedArtifact_throws() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactStore.find("default", 9L))
                .thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(1L).status("ARCHIVED").build()));

        // then
        assertThatThrownBy(() -> sut.attach("eng", 100L, new AttachResourceRequest(7L, 9L)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("ARTIFACT_ARCHIVED");
    }

    @Test
    void attach_new_savesLink() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactStore.find("default", 9L))
                .thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(1L).build()));
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, true));
        when(sessionAccess.sessionIdOfSlot(7L)).thenReturn(Optional.of(100L));
        when(attachmentStore.find("default", 9L, 100L, 7L)).thenReturn(Optional.empty());
        when(attachmentStore.save(any()))
                .thenAnswer(inv -> ((StoredAttachment) inv.getArgument(0)).toBuilder().id(42L).build());

        // when
        var result = sut.attach("eng", 100L, new AttachResourceRequest(7L, 9L));

        // then
        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.attachedByUserId()).isEqualTo("alice");
    }

    @Test
    void attach_sessionLevel_savesLinkWithNullSegment() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactStore.find("default", 9L))
                .thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(1L).build()));
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, true));
        when(attachmentStore.find("default", 9L, 100L, null)).thenReturn(Optional.empty());
        when(attachmentStore.save(any()))
                .thenAnswer(inv -> ((StoredAttachment) inv.getArgument(0)).toBuilder().id(55L).build());

        // when
        var result = sut.attach("eng", 100L, new AttachResourceRequest(null, 9L));

        // then
        assertThat(result.id()).isEqualTo(55L);
        assertThat(result.segmentId()).isNull();
    }

    @Test
    void attach_existingLink_isIdempotent() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactStore.find("default", 9L))
                .thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(1L).build()));
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, true));
        when(sessionAccess.sessionIdOfSlot(7L)).thenReturn(Optional.of(100L));
        when(attachmentStore.find("default", 9L, 100L, 7L))
                .thenReturn(Optional.of(StoredAttachmentMother.any().id(42L).artifactId(9L).build()));

        // when
        var result = sut.attach("eng", 100L, new AttachResourceRequest(7L, 9L));

        // then
        assertThat(result.id()).isEqualTo(42L);
        verify(attachmentStore, never()).save(any());
    }

    @Test
    void attach_sessionFromAnotherCommunity_throws() {

        // given - artifact checks pass but the session belongs to community 2
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactStore.find("default", 9L))
                .thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(1L).build()));
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 2L, false));

        // when / then
        assertThatThrownBy(() -> sut.attach("eng", 100L, new AttachResourceRequest(null, 9L)))
                .isInstanceOf(NotFoundException.class);
        verify(attachmentStore, never()).save(any());
    }

    @Test
    void attach_segmentOutsideTheSession_throws() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactStore.find("default", 9L))
                .thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(1L).build()));
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, true));
        when(sessionAccess.sessionIdOfSlot(7L)).thenReturn(Optional.of(999L));

        // when / then
        assertThatThrownBy(() -> sut.attach("eng", 100L, new AttachResourceRequest(7L, 9L)))
                .isInstanceOf(ValidationException.class);
        verify(attachmentStore, never()).save(any());
    }

    /**
     * Attaching before a session is held is documented as schedule-manager-gated; a plain member with no
     * schedule-management rights must be refused rather than only hidden from by the frontend.
     */
    @Test
    void attach_notScheduleManagerBeforeHeld_throws() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactStore.find("default", 9L))
                .thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(1L).build()));
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, false));
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, false, false));

        // when / then
        assertThatThrownBy(() -> sut.attach("eng", 100L, new AttachResourceRequest(7L, 9L)))
                .isInstanceOf(ForbiddenException.class);
        verify(attachmentStore, never()).save(any());
    }

    @Test
    void attach_organizerBeforeHeld_succeeds() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactStore.find("default", 9L))
                .thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(1L).build()));
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, false));
        when(sessionAccess.sessionIdOfSlot(7L)).thenReturn(Optional.of(100L));
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(true);
        when(attachmentStore.find("default", 9L, 100L, 7L)).thenReturn(Optional.empty());
        when(attachmentStore.save(any()))
                .thenAnswer(inv -> ((StoredAttachment) inv.getArgument(0)).toBuilder().id(43L).build());

        // when
        var result = sut.attach("eng", 100L, new AttachResourceRequest(7L, 9L));

        // then
        assertThat(result.id()).isEqualTo(43L);
    }

    @Test
    void attach_scheduleManagerPolicyBeforeHeld_succeeds() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(artifactStore.find("default", 9L))
                .thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(1L).build()));
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, false));
        when(sessionAccess.sessionIdOfSlot(7L)).thenReturn(Optional.of(100L));
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, false, true));
        when(attachmentStore.find("default", 9L, 100L, 7L)).thenReturn(Optional.empty());
        when(attachmentStore.save(any()))
                .thenAnswer(inv -> ((StoredAttachment) inv.getArgument(0)).toBuilder().id(44L).build());

        // when
        var result = sut.attach("eng", 100L, new AttachResourceRequest(7L, 9L));

        // then
        assertThat(result.id()).isEqualTo(44L);
    }

    @Test
    void resourcesForSession_sessionFromAnotherCommunity_throws() {

        // given
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 2L, false));

        // when / then
        assertThatThrownBy(() -> sut.resourcesForSession("eng", 100L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void detach_attachedRow_answersToTheAttacher() {

        // given
        when(currentUser.userId()).thenReturn("carol");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, true));
        var artifact = StoredArtifactMother.any().id(9L).communityId(1L).contributorUserId("bob").build();
        when(artifactStore.find("default", 9L)).thenReturn(Optional.of(artifact));
        var attachment = StoredAttachmentMother.any().id(42L).artifactId(9L).attachedByUserId("alice").build();
        when(attachmentStore.find("default", 9L, 100L, 7L)).thenReturn(Optional.of(attachment));
        when(communityAccess.isOrganizer(1L, "carol")).thenReturn(true);

        // when
        sut.detach("eng", 100L, 9L, 7L);

        // then
        verify(attachmentStore).delete(attachment);
        verify(artifactStore, never()).save(any());
    }

    @Test
    void detach_originOnlyRow_answersToTheContributor_andClearsTheOrigin() {

        // given
        when(currentUser.userId()).thenReturn("carol");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, true));
        var artifact = StoredArtifactMother.any().id(9L).communityId(1L).contributorUserId("bob")
                .provenanceSessionId(100L).provenanceSegmentId(7L).provenanceLabel("Talk").build();
        when(artifactStore.find("default", 9L)).thenReturn(Optional.of(artifact));
        when(attachmentStore.find("default", 9L, 100L, 7L)).thenReturn(Optional.empty());
        when(communityAccess.isOrganizer(1L, "carol")).thenReturn(true);
        var saved = ArgumentCaptor.forClass(StoredArtifact.class);

        // when
        sut.detach("eng", 100L, 9L, 7L);

        // then
        verify(artifactStore).save(saved.capture());
        assertThat(saved.getValue().provenanceSessionId()).isNull();
        assertThat(saved.getValue().provenanceSegmentId()).isNull();
        assertThat(saved.getValue().provenanceLabel()).isNull();
        verify(attachmentStore, never()).delete(any());
    }

    @Test
    void detach_notOnThisSession_throwsNotFound() {

        // given
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, true));
        when(artifactStore.find("default", 9L))
                .thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(1L).build()));
        when(attachmentStore.find("default", 9L, 100L, 7L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.detach("eng", 100L, 9L, 7L)).isInstanceOf(NotFoundException.class);
        verify(artifactStore, never()).save(any());
    }

    @Test
    void detach_artifactOfAnotherCommunity_throwsNotFound() {

        // given
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, true));
        when(artifactStore.find("default", 9L))
                .thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(2L).build()));

        // when / then
        assertThatThrownBy(() -> sut.detach("eng", 100L, 9L, 7L)).isInstanceOf(NotFoundException.class);
        verify(attachmentStore, never()).delete(any());
    }

    @Test
    void detach_sessionOfAnotherCommunity_throwsNotFound() {

        // given
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 2L, true));

        // when / then
        assertThatThrownBy(() -> sut.detach("eng", 100L, 9L, 7L)).isInstanceOf(NotFoundException.class);
        verify(attachmentStore, never()).delete(any());
    }

    @Test
    void detach_refusedCaller_changesNothing() {

        // given - carol is neither the attacher, the contributor nor an organizer
        when(currentUser.userId()).thenReturn("carol");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, true));
        when(artifactStore.find("default", 9L)).thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(1L)
                .contributorUserId("bob").provenanceSessionId(100L).build()));
        var attachment = StoredAttachmentMother.any().id(42L).artifactId(9L).attachedByUserId("alice").build();
        when(attachmentStore.find("default", 9L, 100L, 7L)).thenReturn(Optional.of(attachment));

        // when / then
        assertThatThrownBy(() -> sut.detach("eng", 100L, 9L, 7L)).isInstanceOf(ForbiddenException.class)
                .hasMessage("Only whoever attached it or an organizer may remove this attachment");
        verify(attachmentStore, never()).delete(any());
        verify(artifactStore, never()).save(any());
    }

    /**
     * An attacher may remove what they attached without also clearing an origin that is the contributor's to remove.
     */
    @Test
    void detach_attacherWithoutContributorRights_removesOnlyTheAttachment() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, true));
        var artifact = StoredArtifactMother.any().id(9L).communityId(1L).contributorUserId("bob")
                .provenanceSessionId(100L).provenanceSegmentId(7L).provenanceLabel("Talk").build();
        when(artifactStore.find("default", 9L)).thenReturn(Optional.of(artifact));
        var attachment = StoredAttachmentMother.any().id(42L).artifactId(9L).attachedByUserId("alice").build();
        when(attachmentStore.find("default", 9L, 100L, 7L)).thenReturn(Optional.of(attachment));

        // when
        sut.detach("eng", 100L, 9L, 7L);

        // then - bob (the contributor) never authorized the origin clear, so it stays untouched
        verify(attachmentStore).delete(attachment);
        verify(artifactStore, never()).save(any());
    }

    /** A contributor with no attacher rights may not remove someone else's attachment; nothing is written. */
    @Test
    void detach_contributorWithoutAttacherRights_isForbidden_leavesAttachmentAndOrigin() {

        // given
        when(currentUser.userId()).thenReturn("bob");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, true));
        var artifact = StoredArtifactMother.any().id(9L).communityId(1L).contributorUserId("bob")
                .provenanceSessionId(100L).provenanceSegmentId(7L).provenanceLabel("Talk").build();
        when(artifactStore.find("default", 9L)).thenReturn(Optional.of(artifact));
        var attachment = StoredAttachmentMother.any().id(42L).artifactId(9L).attachedByUserId("alice").build();
        when(attachmentStore.find("default", 9L, 100L, 7L)).thenReturn(Optional.of(attachment));

        // when / then - bob is the contributor, but the row detached is Alice's attachment, not the origin
        assertThatThrownBy(() -> sut.detach("eng", 100L, 9L, 7L)).isInstanceOf(ForbiddenException.class)
                .hasMessage("Only whoever attached it or an organizer may remove this attachment");
        verify(attachmentStore, never()).delete(any());
        verify(artifactStore, never()).save(any());
    }

    @Test
    void detach_originOnlyRow_refusedCaller_isForbiddenWithTheContributorsRight() {

        // given
        when(currentUser.userId()).thenReturn("carol");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, true));
        when(artifactStore.find("default", 9L)).thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(1L)
                .contributorUserId("bob").provenanceSessionId(100L).build()));
        when(attachmentStore.find("default", 9L, 100L, null)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.detach("eng", 100L, 9L, null)).isInstanceOf(ForbiddenException.class)
                .hasMessage("Only the contributor or an organizer may remove this resource");
        verify(artifactStore, never()).save(any());
    }

    @Test
    void detach_originOnlyRow_underAnotherSegment_throwsNotFound() {

        // given - the origin names talk 7, the request names talk 8
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, true));
        when(artifactStore.find("default", 9L)).thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(1L)
                .contributorUserId("bob").provenanceSessionId(100L).provenanceSegmentId(7L).build()));
        when(attachmentStore.find("default", 9L, 100L, 8L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.detach("eng", 100L, 9L, 8L)).isInstanceOf(NotFoundException.class);
        verify(artifactStore, never()).save(any());
    }

    @Test
    void detach_organizerWhoIsNeitherAttacherNorContributor_readsTheirRoleOnce() {

        // given
        when(currentUser.userId()).thenReturn("carol");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(sessionAccess.session(100L)).thenReturn(new SessionRef(100L, 1L, true));
        when(artifactStore.find("default", 9L)).thenReturn(Optional.of(StoredArtifactMother.any().id(9L).communityId(1L)
                .contributorUserId("bob").provenanceSessionId(100L).provenanceSegmentId(7L).build()));
        var attachment = StoredAttachmentMother.any().id(42L).artifactId(9L).attachedByUserId("alice").build();
        when(attachmentStore.find("default", 9L, 100L, 7L)).thenReturn(Optional.of(attachment));
        when(communityAccess.isOrganizer(1L, "carol")).thenReturn(true);

        // when
        sut.detach("eng", 100L, 9L, 7L);

        // then
        verify(attachmentStore).delete(attachment);
        verify(artifactStore).save(any());
        verify(communityAccess, times(1)).isOrganizer(1L, "carol");
    }
}
