package dev.axonia.knowledge.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.events.ResourceLink;
import dev.axonia.knowledge.mother.ArtifactEntityMother;
import dev.axonia.knowledge.mother.AttachmentEntityMother;

import static org.assertj.core.api.Assertions.assertThat;

// SessionResourceRepositoryImpl is a plain @Repository bean (not a Spring Data fragment), so the
// @DataJdbcTest slice does not register it automatically - import it explicitly, same as
// SessionResourceRepositoryTest.
@Import(SessionResourceRepositoryImpl.class)
class SessionResourceAccessAdapterTest extends CoreJdbcTestSupport {

    @Autowired
    private SessionResourceRepository sessionResourceRepository;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    private SessionResourceAccessAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new SessionResourceAccessAdapter(sessionResourceRepository, artifactRepository);
    }

    private Long artifact(String title, String url) {

        return artifactRepository
                .save(ArtifactEntityMother.any().title(title).url(url).searchDocument("x").tags(Set.of()).build())
                .getId();
    }

    private Long artifact(String url) {

        return artifact("Resilience patterns talk", url);
    }

    private void attach(Long artifactId, Long sessionId, Long segmentId) {

        attachmentRepository.save(
                AttachmentEntityMother.any().artifactId(artifactId).sessionId(sessionId).segmentId(segmentId).build());
    }

    @Test
    void groupsResourceLinksBySlot() {

        var deck = artifact("Deck", "https://example.com/deck.pdf");
        attach(deck, 100L, 7L);

        var actual = sut.resourcesForSession("default", 100L, 1L);

        assertThat(actual.bySlot()).containsEntry(7L,
                List.of(new ResourceLink("Deck", "https://example.com/deck.pdf")));
        assertThat(actual.sessionLevel()).isEmpty();
    }

    @Test
    void multipleResourcesOnOneSlotAllAppear() {

        var deck = artifact("Deck", "https://example.com/deck.pdf");
        var repo = artifact("Repo", "https://example.com/repo");
        attach(deck, 100L, 7L);
        attach(repo, 100L, 7L);

        var actual = sut.resourcesForSession("default", 100L, 1L);

        assertThat(actual.bySlot().get(7L)).containsExactlyInAnyOrder(
                new ResourceLink("Deck", "https://example.com/deck.pdf"),
                new ResourceLink("Repo", "https://example.com/repo"));
    }

    @Test
    void resourcesOnDifferentSlotsAreGroupedSeparately() {

        var deck = artifact("Deck", "https://example.com/deck.pdf");
        var handout = artifact("Handout", "https://example.com/handout.pdf");
        attach(deck, 100L, 7L);
        attach(handout, 100L, 8L);

        var actual = sut.resourcesForSession("default", 100L, 1L);

        assertThat(actual.bySlot()).containsEntry(7L, List.of(new ResourceLink("Deck", "https://example.com/deck.pdf")))
                .containsEntry(8L, List.of(new ResourceLink("Handout", "https://example.com/handout.pdf")));
    }

    @Test
    void aSessionWithNoResourcesReturnsBothHalvesEmpty() {

        var actual = sut.resourcesForSession("default", 100L, 1L);

        assertThat(actual.sessionLevel()).isEmpty();
        assertThat(actual.bySlot()).isEmpty();
    }

    /**
     * The defect this port exists to fix: an attachment made to the session rather than to a segment used to be
     * silently dropped ({@code segmentId == null} returned early). It now lands in {@code sessionLevel}, alongside a
     * segment-attached resource that still lands in {@code bySlot} - one entry in each half from a single session.
     */
    @Test
    void aSessionLevelAndASlotLevelResourceEachLandInTheirOwnHalf() {

        var deck = artifact("Deck", "https://example.com/deck.pdf");
        attach(deck, 100L, 7L);
        var agenda = artifact("Agenda", "https://example.com/agenda.pdf");
        attach(agenda, 100L, null);

        var actual = sut.resourcesForSession("default", 100L, 1L);

        assertThat(actual.bySlot())
                .containsExactly(Map.entry(7L, List.of(new ResourceLink("Deck", "https://example.com/deck.pdf"))));
        assertThat(actual.sessionLevel()).containsExactly(new ResourceLink("Agenda", "https://example.com/agenda.pdf"));
    }

    @Test
    void aResourceWithABlankUrlIsDroppedFromBothHalves() {

        var blankSlotResource = artifact("Undated slot resource", "");
        attach(blankSlotResource, 100L, 7L);
        var blankSessionResource = artifact("Undated session resource", "");
        attach(blankSessionResource, 100L, null);

        var actual = sut.resourcesForSession("default", 100L, 1L);

        assertThat(actual.bySlot()).isEmpty();
        assertThat(actual.sessionLevel()).isEmpty();
    }

    @Test
    void anArtifactTheRepositoryCannotFindIsDropped() {

        // The attachment row exists and points at a real artifact id - just not one findByOrganizationIdAndId
        // can see for "default": the artifact itself was created under a different organization. Same "gone
        // as far as this adapter is concerned" outcome as an id the schema no longer has at all.
        var otherOrgArtifact = artifactRepository.save(ArtifactEntityMother.any().organizationId("other-org")
                .url("https://example.com/leak.pdf").searchDocument("x").tags(Set.of()).build()).getId();
        attachmentRepository.save(AttachmentEntityMother.any().organizationId("default").artifactId(otherOrgArtifact)
                .sessionId(100L).segmentId(7L).build());

        assertThat(sut.resourcesForSession("default", 100L, 1L).bySlot()).isEmpty();
    }

    @Test
    void bothOrganizationPredicatesIndependentlyKeepTheWrongTenantsResourceOut() {

        // A single internally-consistent "other-org" attachment+artifact pair is NOT a sufficient regression
        // test: either predicate alone already excludes it, so dropping one predicate leaves the other covering
        // for it and the test stays green. Isolating each predicate needs the organization mismatch to sit on a
        // different side of the attachment-to-artifact join for each case. This test connects as the schema
        // owner (JdbcTestSupport), which bypasses RLS entirely by ownership - the one connection where a dropped
        // predicate would otherwise go completely unnoticed.
        var ownUrl = "https://example.com/own.pdf";
        var ownArtifact = artifact("Own", ownUrl);
        attach(ownArtifact, 100L, 7L);

        // Case (a): the ATTACHMENT is recorded under "default" but points at an artifact actually owned by
        // "other-org". SessionResourceRepositoryImpl's SQL predicate (att.organization_id = :organizationId)
        // admits this row on its own - the attachment's own organization_id genuinely is "default" - so only
        // ArtifactRepository.findByOrganizationIdAndId's own organization predicate keeps this URL out.
        var caseAUrl = "https://example.com/case-a-leak.pdf";
        var caseAArtifact = artifactRepository.save(ArtifactEntityMother.any().organizationId("other-org").url(caseAUrl)
                .searchDocument("x").tags(Set.of()).build()).getId();
        attachmentRepository.save(AttachmentEntityMother.any().organizationId("default").artifactId(caseAArtifact)
                .sessionId(100L).segmentId(7L).build());

        // Case (b): the ATTACHMENT is recorded under "other-org" but points at an artifact actually owned by
        // "default". Only SessionResourceRepositoryImpl's SQL predicate keeps this row out of findForSession's
        // result at all - if it were dropped, the artifact lookup alone would happily resolve it, since it
        // genuinely belongs to "default".
        var caseBUrl = "https://example.com/case-b-leak.pdf";
        var caseBArtifact = artifact("Case b", caseBUrl);
        attachmentRepository.save(AttachmentEntityMother.any().organizationId("other-org").artifactId(caseBArtifact)
                .sessionId(100L).segmentId(7L).build());

        var actual = sut.resourcesForSession("default", 100L, 1L);

        assertThat(actual.bySlot().get(7L)).containsExactly(new ResourceLink("Own", ownUrl));
    }

    /**
     * A second read surface: a resource attached under a session id naming a different community used to still render
     * in that session's wrap-up mail. {@code SessionFactsAssembler} passes the session's own community id into this
     * port; here that arrives as the {@code communityId} argument this adapter now forwards to
     * {@link SessionResourceRepository#findForSession}.
     */
    @Test
    void communityFiltered_artifactBelongsToADifferentCommunity_excludesRow() {

        var planted = artifactRepository.save(ArtifactEntityMother.any().communityId(2L).title("Planted")
                .url("https://example.com/planted.pdf").searchDocument("x").tags(Set.of()).build()).getId();
        attach(planted, 100L, 7L);

        var actual = sut.resourcesForSession("default", 100L, 1L);

        assertThat(actual.bySlot()).isEmpty();
        assertThat(actual.sessionLevel()).isEmpty();
    }
}
