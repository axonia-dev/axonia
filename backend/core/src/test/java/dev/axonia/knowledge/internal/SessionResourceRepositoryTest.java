package dev.axonia.knowledge.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.knowledge.mother.ArtifactEntityMother;
import dev.axonia.knowledge.mother.AttachmentEntityMother;

import static org.assertj.core.api.Assertions.assertThat;

// SessionResourceRepositoryImpl is a plain @Repository bean (not a Spring Data fragment), so the
// @DataJdbcTest slice does not register it automatically — import it explicitly.
@Import(SessionResourceRepositoryImpl.class)
class SessionResourceRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private SessionResourceRepository sessionResourceRepository;
    @Autowired
    private ArtifactRepository artifactRepository;
    @Autowired
    private AttachmentRepository attachmentRepository;

    private Long artifact(String title) {

        return artifactRepository
                .save(ArtifactEntityMother.any().title(title).searchDocument(title).tags(java.util.Set.of()).build())
                .getId();
    }

    @Test
    void returnsAttachedActiveArtifactsWithSegment() {

        // given
        var aId = artifact("Deck");
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(aId).sessionId(100L).segmentId(7L).build());

        // when
        var rows = sessionResourceRepository.findForSession("default", 100L, 1L);

        // then
        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.artifactId()).isEqualTo(aId);
            assertThat(r.segmentId()).isEqualTo(7L);
            assertThat(r.attachmentId()).isNotNull();
            assertThat(r.attachedByUserId()).isEqualTo("dev-user");
        });
    }

    @Test
    void excludesArchivedAttachedArtifacts() {

        // given
        var aId = artifactRepository.save(
                ArtifactEntityMother.any().status("ARCHIVED").searchDocument("x").tags(java.util.Set.of()).build())
                .getId();
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(aId).sessionId(100L).segmentId(7L).build());

        // then
        assertThat(sessionResourceRepository.findForSession("default", 100L, 1L)).isEmpty();
    }

    @Test
    void includesProvenanceOnlyOrphanWithNullAttachmentId() {

        // given
        var aId = artifactRepository.save(ArtifactEntityMother.any().title("Legacy").searchDocument("Legacy")
                .tags(java.util.Set.of()).provenanceSessionId(100L).provenanceSegmentId(null).build()).getId();

        // when
        var rows = sessionResourceRepository.findForSession("default", 100L, 1L);

        // then
        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.artifactId()).isEqualTo(aId);
            assertThat(r.attachmentId()).isNull();
            assertThat(r.segmentId()).isNull();
        });
    }

    @Test
    void deduplicates_attachedArtifactThatAlsoHasProvenance_appearsOnceFromAttachment() {

        // given
        var aId = artifactRepository.save(ArtifactEntityMother.any().searchDocument("x").tags(java.util.Set.of())
                .provenanceSessionId(100L).provenanceSegmentId(7L).build()).getId();
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(aId).sessionId(100L).segmentId(7L).build());

        // when
        var rows = sessionResourceRepository.findForSession("default", 100L, 1L);

        // then
        assertThat(rows).singleElement().satisfies(r -> assertThat(r.attachmentId()).isNotNull());
    }

    @Test
    void sameArtifactOnTwoTalks_returnsTwoRows() {

        // given
        var aId = artifact("Shared");
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(aId).sessionId(100L).segmentId(7L).build());
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(aId).sessionId(100L).segmentId(8L).build());

        // then
        assertThat(sessionResourceRepository.findForSession("default", 100L, 1L))
                .extracting(SessionResourceRow::segmentId).containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    void communityFiltered_matchingCommunity_includesAttachedArtifact() {

        // given
        var aId = artifactRepository.save(ArtifactEntityMother.any().communityId(1L).title("Deck")
                .searchDocument("Deck").tags(java.util.Set.of()).build()).getId();
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(aId).sessionId(100L).segmentId(7L).build());

        // when
        var rows = sessionResourceRepository.findForSession("default", 100L, 1L);

        // then
        assertThat(rows).singleElement().satisfies(r -> assertThat(r.artifactId()).isEqualTo(aId));
    }

    /**
     * The read-side half of the cross-community guard: {@code SessionProvenanceGuard} stops a NEW cross-community link
     * from being written, but a row planted before the guard existed still sits in the table. Querying with the
     * session's real owning community now excludes it, even though the attachment's own organization_id and session_id
     * both match.
     */
    @Test
    void communityFiltered_artifactBelongsToADifferentCommunity_excludesRow() {

        // given - an artifact planted (pre-fix) under a community that does not own session 100
        var aId = artifactRepository.save(ArtifactEntityMother.any().communityId(2L).title("Planted")
                .searchDocument("Planted").tags(java.util.Set.of()).build()).getId();
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(aId).sessionId(100L).segmentId(7L).build());

        // when - session 100 is queried by the community that actually owns it
        var rows = sessionResourceRepository.findForSession("default", 100L, 1L);

        // then
        assertThat(rows).isEmpty();
    }

    @Test
    void communityFiltered_matchingCommunity_includesProvenanceOnlyOrphan() {

        // given - the provenance-only half of the union is a separate SELECT from the attached half above; if it were
        // over-constrained (or under-constrained), this case would not catch it from the other half's tests alone
        var aId = artifactRepository
                .save(ArtifactEntityMother.any().communityId(1L).title("Legacy").searchDocument("Legacy")
                        .tags(java.util.Set.of()).provenanceSessionId(100L).provenanceSegmentId(null).build())
                .getId();

        // when
        var rows = sessionResourceRepository.findForSession("default", 100L, 1L);

        // then
        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.artifactId()).isEqualTo(aId);
            assertThat(r.attachmentId()).isNull();
        });
    }

    @Test
    void communityFiltered_appliesToProvenanceOnlyOrphanToo() {

        // given - a provenance-only orphan (no attachment row at all) planted under a community that does not own
        // session 100; without its own AND a.community_id on this half of the union, this row would still render
        artifactRepository.save(ArtifactEntityMother.any().communityId(2L).title("Planted").searchDocument("Planted")
                .tags(java.util.Set.of()).provenanceSessionId(100L).provenanceSegmentId(null).build());

        // when
        var rows = sessionResourceRepository.findForSession("default", 100L, 1L);

        // then
        assertThat(rows).isEmpty();
    }
}
