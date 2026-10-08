package dev.axonia.knowledge.internal;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.knowledge.CommunityAccess;
import dev.axonia.knowledge.CurrentUser;
import dev.axonia.knowledge.OrganizationContext;
import dev.axonia.knowledge.ResourceService;
import dev.axonia.knowledge.SessionAccess;
import dev.axonia.knowledge.SessionRef;
import dev.axonia.knowledge.mother.ArtifactEntityMother;
import dev.axonia.knowledge.mother.AttachmentEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Removing an artifact from a talk against real Postgres, where the resource list is a UNION of two tables. */
class ResourceDetachJdbcTest extends CoreJdbcTestSupport {

    private static final long COMMUNITY = 1L;
    private static final long SESSION = 100L;
    private static final long TALK = 7L;

    @Autowired
    private ArtifactRepository artifactRepository;
    @Autowired
    private AttachmentRepository attachmentRepository;
    @Autowired
    private ArtifactTypeRepository artifactTypeRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private final CommunityAccess communityAccess = mock(CommunityAccess.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final OrganizationContext organizationContext = mock(OrganizationContext.class);
    private final SessionAccess sessionAccess = mock(SessionAccess.class);

    private SessionResourceRepository sessionResources;
    private ResourceService sut;

    @BeforeEach
    void assembleService() {

        sessionResources = new SessionResourceRepositoryImpl(jdbc);
        var guard = new SessionProvenanceGuard(sessionAccess);
        sut = new ResourceService(attachmentRepository, sessionResources, artifactRepository,
                new ArtifactMapper(artifactTypeRepository), new ArtifactAuthorizer(communityAccess), communityAccess,
                currentUser, organizationContext, guard, new ResourceAttachAuthorizer(communityAccess, guard));
        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(COMMUNITY);
        when(sessionAccess.session(SESSION)).thenReturn(new SessionRef(SESSION, COMMUNITY, true));
        when(currentUser.userId()).thenReturn("dev-user");
    }

    /** What "Add resource" on a talk writes: the artifact carrying its origin, and the attachment beside it. */
    private Long capturedOntoTalk(long sessionId, long segmentId) {

        var artifactId = artifactRepository.save(ArtifactEntityMother.any().title("Spring Boot deck")
                .searchDocument("Spring Boot deck").tags(Set.of()).provenanceSessionId(sessionId)
                .provenanceSegmentId(segmentId).provenanceLabel("Lightning Talk").build()).getId();
        attachmentRepository.save(
                AttachmentEntityMother.any().artifactId(artifactId).sessionId(sessionId).segmentId(segmentId).build());
        return artifactId;
    }

    @Test
    void aResourceCapturedOntoATalkLeavesTheTalkWhenDetached() {

        // given
        var artifactId = capturedOntoTalk(SESSION, TALK);

        // when
        sut.detach("eng", SESSION, artifactId, TALK);

        // then
        assertThat(sessionResources.findForSession("default", SESSION, COMMUNITY))
                .as("a detached resource is gone from the talk, not back without its control").isEmpty();
        assertThat(artifactRepository.findByOrganizationIdAndId("default", artifactId)).get().satisfies(artifact -> {
            assertThat(artifact.getProvenanceSessionId()).isNull();
            assertThat(artifact.getProvenanceSegmentId()).isNull();
            assertThat(artifact.getProvenanceLabel()).isNull();
        });
    }

    @Test
    void aResourceShownOnlyBecauseItCameFromTheSessionCanBeRemoved() {

        // given - captured in the library naming this session, never attached
        var artifactId = artifactRepository.save(ArtifactEntityMother.any().title("Legacy notes")
                .searchDocument("Legacy notes").tags(Set.of()).provenanceSessionId(SESSION).build()).getId();

        // when
        sut.detach("eng", SESSION, artifactId, null);

        // then
        assertThat(sessionResources.findForSession("default", SESSION, COMMUNITY)).isEmpty();
        assertThat(artifactRepository.findByOrganizationIdAndId("default", artifactId)).get()
                .extracting(ArtifactEntity::getProvenanceSessionId).isNull();
    }

    /** The default {@code currentUser} ("dev-user") is this artifact's contributor, so the origin may be cleared. */
    @Test
    void anOriginNamingAnotherTalkDoesNotResurfaceAfterDetaching() {

        // given - came from talk 7, attached only to talk 8
        var artifactId = artifactRepository
                .save(ArtifactEntityMother.any().title("Moved deck").searchDocument("Moved deck").tags(Set.of())
                        .provenanceSessionId(SESSION).provenanceSegmentId(TALK).build())
                .getId();
        attachmentRepository
                .save(AttachmentEntityMother.any().artifactId(artifactId).sessionId(SESSION).segmentId(8L).build());

        // when
        sut.detach("eng", SESSION, artifactId, 8L);

        // then
        assertThat(sessionResources.findForSession("default", SESSION, COMMUNITY)).isEmpty();
    }

    @Test
    void anAttachmentOnAnotherTalkOfTheSameSessionStays() {

        // given
        var artifactId = capturedOntoTalk(SESSION, TALK);
        attachmentRepository
                .save(AttachmentEntityMother.any().artifactId(artifactId).sessionId(SESSION).segmentId(8L).build());

        // when
        sut.detach("eng", SESSION, artifactId, TALK);

        // then
        assertThat(sessionResources.findForSession("default", SESSION, COMMUNITY)).singleElement().satisfies(row -> {
            assertThat(row.segmentId()).isEqualTo(8L);
            assertThat(row.attachmentId()).isNotNull();
        });
    }

    @Test
    void anOriginInAnotherSessionIsKept() {

        // given - came from session 200, attached here to session 100
        var artifactId = artifactRepository
                .save(ArtifactEntityMother.any().title("Reused deck").searchDocument("Reused deck").tags(Set.of())
                        .provenanceSessionId(200L).provenanceSegmentId(3L).provenanceLabel("Keynote").build())
                .getId();
        attachmentRepository
                .save(AttachmentEntityMother.any().artifactId(artifactId).sessionId(SESSION).segmentId(TALK).build());

        // when
        sut.detach("eng", SESSION, artifactId, TALK);

        // then - a no-op detach would leave the row visible too, so the attachment's actual removal is asserted
        assertThat(sessionResources.findForSession("default", SESSION, COMMUNITY)).isEmpty();
        assertThat(artifactRepository.findByOrganizationIdAndId("default", artifactId)).get().satisfies(artifact -> {
            assertThat(artifact.getProvenanceSessionId()).isEqualTo(200L);
            assertThat(artifact.getProvenanceLabel()).isEqualTo("Keynote");
        });
    }

    /** Alice attaches Bob's origin-carrying artifact to another talk; her detach may not clear Bob's origin. */
    @Test
    void anAttacherWhoIsNeitherContributorNorOrganizerCannotClearTheOriginFromAnotherTalk() {

        // given
        var artifactId = artifactRepository.save(ArtifactEntityMother.any().title("Shared deck")
                .searchDocument("Shared deck").tags(Set.of()).contributorUserId("bob").provenanceSessionId(SESSION)
                .provenanceSegmentId(TALK).provenanceLabel("Lightning Talk").build()).getId();
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(artifactId).sessionId(SESSION).segmentId(8L)
                .attachedByUserId("alice").build());
        when(currentUser.userId()).thenReturn("alice");

        // when
        sut.detach("eng", SESSION, artifactId, 8L);

        // then - Alice's own attachment on talk 8 is gone, but Bob's origin naming talk 7 survives
        assertThat(sessionResources.findForSession("default", SESSION, COMMUNITY)).singleElement()
                .satisfies(row -> assertThat(row.segmentId()).isEqualTo(TALK));
        assertThat(artifactRepository.findByOrganizationIdAndId("default", artifactId)).get().satisfies(artifact -> {
            assertThat(artifact.getProvenanceSessionId()).isEqualTo(SESSION);
            assertThat(artifact.getProvenanceSegmentId()).isEqualTo(TALK);
        });
    }

    /** Same rule when the attachment and the origin name the very same talk: only the attachment goes. */
    @Test
    void anAttacherWhoIsNeitherContributorNorOrganizerCannotClearTheOriginFromItsOwnTalk() {

        // given
        var artifactId = artifactRepository.save(ArtifactEntityMother.any().title("Shared deck")
                .searchDocument("Shared deck").tags(Set.of()).contributorUserId("bob").provenanceSessionId(SESSION)
                .provenanceSegmentId(TALK).provenanceLabel("Lightning Talk").build()).getId();
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(artifactId).sessionId(SESSION).segmentId(TALK)
                .attachedByUserId("alice").build());
        when(currentUser.userId()).thenReturn("alice");

        // when
        sut.detach("eng", SESSION, artifactId, TALK);

        // then - the attachment is gone, but the row survives through Bob's still-intact origin
        assertThat(sessionResources.findForSession("default", SESSION, COMMUNITY)).singleElement().satisfies(row -> {
            assertThat(row.segmentId()).isEqualTo(TALK);
            assertThat(row.attachmentId()).isNull();
        });
        assertThat(artifactRepository.findByOrganizationIdAndId("default", artifactId)).get()
                .extracting(ArtifactEntity::getProvenanceSessionId).isEqualTo(SESSION);
    }

    /** A contributor with no attacher rights may not remove someone else's attachment; nothing changes. */
    @Test
    void aContributorWhoDidNotAttachCannotRemoveSomeoneElsesAttachment() {

        // given - Bob's artifact originates from talk 7; Alice separately attaches it to talk 8
        var artifactId = artifactRepository.save(ArtifactEntityMother.any().title("Shared deck")
                .searchDocument("Shared deck").tags(Set.of()).contributorUserId("bob").provenanceSessionId(SESSION)
                .provenanceSegmentId(TALK).provenanceLabel("Lightning Talk").build()).getId();
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(artifactId).sessionId(SESSION).segmentId(8L)
                .attachedByUserId("alice").build());
        when(currentUser.userId()).thenReturn("bob");

        // when / then - nothing changes: Alice's attachment survives (the origin stays hidden behind it), Bob's origin
        // is untouched
        assertThatThrownBy(() -> sut.detach("eng", SESSION, artifactId, 8L)).isInstanceOf(ForbiddenException.class);
        assertThat(sessionResources.findForSession("default", SESSION, COMMUNITY)).singleElement().satisfies(row -> {
            assertThat(row.segmentId()).isEqualTo(8L);
            assertThat(row.attachmentId()).isNotNull();
        });
        assertThat(artifactRepository.findByOrganizationIdAndId("default", artifactId)).get()
                .extracting(ArtifactEntity::getProvenanceSessionId).isEqualTo(SESSION);
    }

    @Test
    void anOriginOnlyRowAskedForUnderAnotherTalkIsNotFoundAndKept() {

        // given - captured onto talk 7, never attached
        var artifactId = artifactRepository
                .save(ArtifactEntityMother.any().title("Talk notes").searchDocument("Talk notes").tags(Set.of())
                        .provenanceSessionId(SESSION).provenanceSegmentId(TALK).build())
                .getId();

        // when / then
        assertThatThrownBy(() -> sut.detach("eng", SESSION, artifactId, 8L)).isInstanceOf(NotFoundException.class);
        assertThat(sessionResources.findForSession("default", SESSION, COMMUNITY)).singleElement()
                .satisfies(row -> assertThat(row.segmentId()).isEqualTo(TALK));
    }

    /** An organizer, neither the attacher nor the contributor, removes both the attachment and the origin it hid. */
    @Test
    void anOrganizerWhoIsNeitherAttacherNorContributorRemovesBothAttachmentAndOrigin() {

        // given - Bob's origin and Alice's attachment both name the same talk
        var artifactId = artifactRepository.save(ArtifactEntityMother.any().title("Shared deck")
                .searchDocument("Shared deck").tags(Set.of()).contributorUserId("bob").provenanceSessionId(SESSION)
                .provenanceSegmentId(TALK).provenanceLabel("Lightning Talk").build()).getId();
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(artifactId).sessionId(SESSION).segmentId(TALK)
                .attachedByUserId("alice").build());
        when(currentUser.userId()).thenReturn("carol");
        when(communityAccess.isOrganizer(COMMUNITY, "carol")).thenReturn(true);

        // when
        sut.detach("eng", SESSION, artifactId, TALK);

        // then
        assertThat(sessionResources.findForSession("default", SESSION, COMMUNITY)).isEmpty();
        assertThat(artifactRepository.findByOrganizationIdAndId("default", artifactId)).get()
                .extracting(ArtifactEntity::getProvenanceSessionId).isNull();
    }
}
