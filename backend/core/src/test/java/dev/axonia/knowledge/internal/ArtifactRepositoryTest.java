package dev.axonia.knowledge.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.knowledge.ArtifactFilter;
import dev.axonia.knowledge.ArtifactSession;
import dev.axonia.knowledge.mother.ArtifactEntityMother;
import dev.axonia.knowledge.mother.AttachmentEntityMother;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private ArtifactRepository artifactRepository;
    @Autowired
    private AttachmentRepository attachmentRepository;
    @Autowired
    private org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;

    @Test
    void savesAndReloadsWithTags() {

        // given
        var saved = artifactRepository.save(ArtifactEntityMother.any().build());

        // when
        var reloaded = artifactRepository.findByOrganizationIdAndId("default", saved.getId()).orElseThrow();

        // then
        assertThat(reloaded.getTitle()).isEqualTo("Resilience patterns talk");
        assertThat(reloaded.getStatus()).isEqualTo("ACTIVE");
        assertThat(reloaded.getTags()).extracting(ArtifactTagRef::getTag).containsExactly("resilience");
    }

    @Test
    void findByOrganizationIdAndId_isTenantScoped() {

        // given
        var saved = artifactRepository.save(ArtifactEntityMother.any().organizationId("acme").build());

        // then
        assertThat(artifactRepository.findByOrganizationIdAndId("default", saved.getId())).isEmpty();
        assertThat(artifactRepository.findByOrganizationIdAndId("acme", saved.getId())).isPresent();
    }

    @Test
    void deletingArtifactCascadesTags() {

        // given
        var saved = artifactRepository.save(ArtifactEntityMother.any().build());

        // when
        artifactRepository.delete(saved);

        // then
        assertThat(artifactRepository.findByOrganizationIdAndId("default", saved.getId())).isEmpty();
    }

    private ArtifactEntity saveArtifact(String title, String description, Long typeId, String contributor,
            String... tags) {

        var refs = Arrays.stream(tags).map(t -> ArtifactTagRef.builder().tag(t).build()).collect(Collectors.toSet());
        var doc = title + " " + description + " " + String.join(" ", tags);
        return artifactRepository.save(ArtifactEntityMother.any().title(title).description(description).typeId(typeId)
                .contributorUserId(contributor).searchDocument(doc).tags(refs).build());
    }

    @Test
    void fullTextSearchMatchesTitleAndDescription() {

        // given
        saveArtifact("Resilience patterns", "handling retries and timeouts", 1L, "alice", "resilience");
        saveArtifact("Lunch and learn", "team social notes", 1L, "bob", "social");

        // when
        var page = artifactRepository.search("default", 1L, ArtifactFilter.builder().q("retries").build(),
                PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).extracting(ArtifactEntity::getTitle).containsExactly("Resilience patterns");
    }

    /**
     * The hydration rejoins {@code artifact_tag} rows onto their artifact by a {@code Map} keyed on {@code
     * artifact_id}, built separately from the roots query - nothing before this test proved the join lands on the right
     * row rather than merely returning the right rows. A tagless artifact must come back with an empty collection, not
     * {@code null} and not a missing map entry.
     */
    @Test
    void search_rejoinsTagsOntoTheRightArtifactIncludingATaglessOne() {

        // given
        var multiTagged = saveArtifact("Java talk", "body", 1L, "alice", "java", "kotlin");
        var singleTagged = saveArtifact("Social event", "body", 1L, "bob", "social");
        var tagless = saveArtifact("No tags", "body", 1L, "carol");

        // when
        var page = artifactRepository.search("default", 1L, ArtifactFilter.builder().build(), PageRequest.of(0, 10));
        var byId = page.getContent().stream().collect(Collectors.toMap(ArtifactEntity::getId, e -> e));

        // then
        assertThat(byId.get(multiTagged.getId()).getTags()).extracting(ArtifactTagRef::getTag)
                .containsExactlyInAnyOrder("java", "kotlin");
        assertThat(byId.get(singleTagged.getId()).getTags()).extracting(ArtifactTagRef::getTag)
                .containsExactly("social");
        assertThat(byId.get(tagless.getId()).getTags()).isNotNull().isEmpty();
    }

    /**
     * {@code fullTextSearchMatchesTitleAndDescription} matches a single row, which cannot distinguish correct {@code
     * ts_rank} order from luck. This seeds four artifacts whose search term frequency differs (Postgres' unnormalized
     * {@code ts_rank} increases with matching-lexeme occurrences), so the order is falsifiable.
     */
    @Test
    void search_rankedOrderReflectsRelevanceAcrossMultipleRows() {

        // given
        saveArtifact("Low relevance", "one mention of retries here", 1L, "alice", "java");
        saveArtifact("High relevance", "retries retries retries strategy for retries", 1L, "bob", "java");
        saveArtifact("Mid relevance", "retries and retries strategy", 1L, "carol", "java");
        saveArtifact("Unrelated", "nothing to do with the search term", 1L, "dave", "java");

        // when
        var page = artifactRepository.search("default", 1L, ArtifactFilter.builder().q("retries").build(),
                PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).extracting(ArtifactEntity::getTitle).containsExactly("High relevance",
                "Mid relevance", "Low relevance");
    }

    /**
     * {@code searchIsPaged} only asserts {@code hasSize}/{@code getTotalElements}, never titles, so it cannot tell the
     * default {@code created_at DESC} order from an accidental one. Timestamps are set explicitly rather than relying
     * on save-order timing, which a fast test run could collapse into the same instant.
     */
    @Test
    void search_defaultOrderIsCreatedAtDescendingAcrossMultipleRows() {

        // given
        var oldest = saveArtifact("Oldest", "body", 1L, "alice", "java");
        var middle = saveArtifact("Middle", "body", 1L, "bob", "java");
        var newest = saveArtifact("Newest", "body", 1L, "carol", "java");
        var now = Instant.now();
        setCreatedAt(oldest.getId(), now.minusSeconds(300));
        setCreatedAt(middle.getId(), now.minusSeconds(150));
        setCreatedAt(newest.getId(), now);

        // when
        var page = artifactRepository.search("default", 1L, ArtifactFilter.builder().build(), PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).extracting(ArtifactEntity::getTitle).containsExactly("Newest", "Middle",
                "Oldest");
    }

    private void setCreatedAt(Long id, Instant createdAt) {

        jdbc.getJdbcTemplate().update("UPDATE artifact SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), id);
    }

    @Test
    void facetsFilterByTypeTagContributor() {

        // given
        saveArtifact("Alpha", "x", 1L, "alice", "java");
        saveArtifact("Beta", "x", 2L, "alice", "kotlin");
        saveArtifact("Gamma", "x", 1L, "bob", "java");

        // when
        var byType = artifactRepository.search("default", 1L, ArtifactFilter.builder().typeId(1L).build(),
                PageRequest.of(0, 10));

        // then
        assertThat(byType.getTotalElements()).isEqualTo(2);

        // when
        var byTag = artifactRepository.search("default", 1L, ArtifactFilter.builder().tag("kotlin").build(),
                PageRequest.of(0, 10));

        // then
        assertThat(byTag.getContent()).extracting(ArtifactEntity::getTitle).containsExactly("Beta");

        // when
        var byContributor = artifactRepository.search("default", 1L,
                ArtifactFilter.builder().contributor("bob").build(), PageRequest.of(0, 10));

        // then
        assertThat(byContributor.getContent()).extracting(ArtifactEntity::getTitle).containsExactly("Gamma");
    }

    @Test
    void searchExcludesArchivedAndOtherTenantsAndOtherCommunities() {

        // given
        saveArtifact("Active here", "x", 1L, "alice", "java");
        artifactRepository.save(ArtifactEntityMother.any().title("Archived").status("ARCHIVED")
                .searchDocument("Archived").tags(Set.of()).build());
        artifactRepository.save(ArtifactEntityMother.any().title("Other community").communityId(2L)
                .searchDocument("Other community").tags(Set.of()).build());
        artifactRepository.save(ArtifactEntityMother.any().title("Other tenant").organizationId("acme")
                .searchDocument("Other tenant").tags(Set.of()).build());

        // when
        var page = artifactRepository.search("default", 1L, ArtifactFilter.builder().build(), PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).extracting(ArtifactEntity::getTitle).containsExactly("Active here");
    }

    @Test
    void searchIsPaged() {

        // given
        for (var i = 0; i < 3; i++) {
            saveArtifact("Doc " + i, "body", 1L, "alice", "java");
        }

        // when
        var page = artifactRepository.search("default", 1L, ArtifactFilter.builder().build(), PageRequest.of(0, 2));

        // then
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    /**
     * A page past the last row is a non-zero total with an empty id page - the one shape the early-return on a zero
     * total does not cover. An empty {@code IN ()} is a syntax error in Postgres, so the hydration has to short-circuit
     * before ever building it, not merely before the count is zero.
     */
    @Test
    void searchPageBeyondTheLastRow_returnsEmptyContentWithTheRealTotal() {

        // given
        saveArtifact("Only one", "body", 1L, "alice", "java");

        // when
        var page = artifactRepository.search("default", 1L, ArtifactFilter.builder().build(), PageRequest.of(1, 10));

        // then
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void blankFiltersAreIgnored() {

        // given
        saveArtifact("Any", "body", 1L, "alice", "java");

        // when
        var page = artifactRepository.search("default", 1L,
                ArtifactFilter.builder().q("  ").tag("  ").contributor("  ").build(), PageRequest.of(0, 10));

        // then
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void searchByDateRange() {

        // given
        saveArtifact("Dated", "body", 1L, "alice", "java");

        // when
        var future = java.time.Instant.now().plusSeconds(3600);
        var none = artifactRepository.search("default", 1L, ArtifactFilter.builder().from(future).build(),
                PageRequest.of(0, 10));

        // then
        assertThat(none.getTotalElements()).isZero();

        // when
        var past = java.time.Instant.now().minusSeconds(3600);
        var all = artifactRepository.search("default", 1L,
                ArtifactFilter.builder().from(past).to(java.time.Instant.now().plusSeconds(3600)).build(),
                PageRequest.of(0, 10));

        // then
        assertThat(all.getTotalElements()).isEqualTo(1);
    }

    private Long artifactWithProvenanceSession(String title, Long sessionId) {

        return artifactRepository.save(ArtifactEntityMother.any().title(title).searchDocument(title).tags(Set.of())
                .provenanceSessionId(sessionId).provenanceSegmentId(null).build()).getId();
    }

    private Long standaloneArtifact(String title) {

        return artifactRepository
                .save(ArtifactEntityMother.any().title(title).searchDocument(title).tags(Set.of()).build()).getId();
    }

    private ArtifactFilter sessionFilter(Long session) {

        return ArtifactFilter.builder().session(session).build();
    }

    @Test
    void sessionFilter_matchesProvenanceSession() {

        // given
        artifactWithProvenanceSession("From session", 100L);
        standaloneArtifact("Unrelated");

        // when
        var page = artifactRepository.search("default", 1L, sessionFilter(100L), PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).extracting(ArtifactEntity::getTitle).containsExactly("From session");
    }

    @Test
    void sessionFilter_matchesAttachmentSession() {

        // given
        var aId = standaloneArtifact("Attached deck");
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(aId).sessionId(100L).segmentId(7L).build());
        standaloneArtifact("Unrelated");

        // when
        var page = artifactRepository.search("default", 1L, sessionFilter(100L), PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).extracting(ArtifactEntity::getTitle).containsExactly("Attached deck");
    }

    @Test
    void sessionFilter_matchesEitherProvenanceOrAttachment_withoutDuplicates() {

        // given
        var aId = artifactWithProvenanceSession("Provenance and attachment", 100L);
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(aId).sessionId(100L).segmentId(7L).build());

        // when
        var page = artifactRepository.search("default", 1L, sessionFilter(100L), PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).extracting(ArtifactEntity::getTitle).containsExactly("Provenance and attachment");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void sessionFilter_zeroSentinel_matchesStandaloneArtifactsOnly() {

        // given
        standaloneArtifact("Standalone");
        artifactWithProvenanceSession("Has provenance", 100L);
        var attached = standaloneArtifact("Has attachment");
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(attached).sessionId(100L).build());

        // when
        var page = artifactRepository.search("default", 1L, sessionFilter(0L), PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).extracting(ArtifactEntity::getTitle).containsExactly("Standalone");
    }

    @Test
    void sessionsWithArtifacts_countsDistinctArtifactsPerSession() {

        // given
        var shared = artifactWithProvenanceSession("Shared", 100L);
        attachmentRepository
                .save(AttachmentEntityMother.any().artifactId(shared).sessionId(100L).segmentId(7L).build());
        var second = standaloneArtifact("Second on 100");
        attachmentRepository
                .save(AttachmentEntityMother.any().artifactId(second).sessionId(100L).segmentId(8L).build());
        artifactWithProvenanceSession("On 200", 200L);
        standaloneArtifact("Standalone");

        // when
        var sessions = artifactRepository.sessionsWithArtifacts("default", 1L);

        // then
        assertThat(sessions).extracting(ArtifactSession::sessionId).containsExactly(100L, 200L);
        assertThat(sessions).filteredOn(s -> s.sessionId() == 100L).singleElement()
                .satisfies(s -> assertThat(s.count()).isEqualTo(2L));
        assertThat(sessions).filteredOn(s -> s.sessionId() == 200L).singleElement()
                .satisfies(s -> assertThat(s.count()).isEqualTo(1L));
    }

    @Test
    void sessionsWithArtifacts_excludesArchivedAndOtherCommunities() {

        // given
        artifactRepository.save(ArtifactEntityMother.any().title("Archived").status("ARCHIVED").searchDocument("x")
                .tags(Set.of()).provenanceSessionId(300L).build());
        artifactRepository.save(ArtifactEntityMother.any().title("Other community").communityId(2L).searchDocument("x")
                .tags(Set.of()).provenanceSessionId(400L).build());

        // then
        assertThat(artifactRepository.sessionsWithArtifacts("default", 1L)).isEmpty();
    }

    @Test
    void stats_countsTotalsSessionsAndThisMonth() {

        // given
        artifactWithProvenanceSession("On 100", 100L);
        var attached = standaloneArtifact("Attached to 100");
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(attached).sessionId(100L).build());
        artifactWithProvenanceSession("On 200", 200L);
        standaloneArtifact("Standalone");

        // when
        var stats = artifactRepository.stats("default", 1L);

        // then
        assertThat(stats.total()).isEqualTo(4L);
        assertThat(stats.sessionCount()).isEqualTo(2L);
        assertThat(stats.capturedThisMonth()).isEqualTo(4L);
    }

    private void setLoveCount(Long id, int count) {

        jdbc.getJdbcTemplate().update("UPDATE artifact SET love_count = ? WHERE id = ?", count, id);
    }

    @Test
    void search_populatesLoveCountFromColumn() {

        // given
        var artifact = saveArtifact("Counted", "body", 1L, "alice", "java");
        setLoveCount(artifact.getId(), 4);

        // when
        var page = artifactRepository.search("default", 1L, ArtifactFilter.builder().build(), PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).singleElement().satisfies(e -> assertThat(e.getLoveCount()).isEqualTo(4));
    }

    @Test
    void search_mostAppreciatedSortsByLoveCountDesc() {

        // given
        var low = saveArtifact("Low", "body", 1L, "alice", "java");
        var high = saveArtifact("High", "body", 1L, "bob", "java");
        var mid = saveArtifact("Mid", "body", 1L, "carol", "java");
        setLoveCount(low.getId(), 1);
        setLoveCount(high.getId(), 9);
        setLoveCount(mid.getId(), 5);

        // when
        var page = artifactRepository.search("default", 1L, ArtifactFilter.builder().sort("mostAppreciated").build(),
                PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).extracting(ArtifactEntity::getTitle).containsExactly("High", "Mid", "Low");
    }

    /**
     * The id query ranks by love count and the hydration batches every id on the page through one {@code IN} read,
     * which does not preserve order on its own - {@code search} has to replay the ranked id order back over the batch.
     * A single page cannot tell that apart from an accidental match, so this pages across the boundary: five artifacts
     * at page size two prove the third-ranked row lands first on page two, not merely somewhere in it.
     */
    @Test
    void search_mostAppreciatedOrderSurvivesThePageBoundary() {

        // given
        var fifth = saveArtifact("Fifth", "body", 1L, "alice", "java");
        var fourth = saveArtifact("Fourth", "body", 1L, "bob", "java");
        var third = saveArtifact("Third", "body", 1L, "carol", "java");
        var second = saveArtifact("Second", "body", 1L, "dave", "java");
        var first = saveArtifact("First", "body", 1L, "erin", "java");
        setLoveCount(fifth.getId(), 1);
        setLoveCount(fourth.getId(), 2);
        setLoveCount(third.getId(), 3);
        setLoveCount(second.getId(), 4);
        setLoveCount(first.getId(), 5);
        var filter = ArtifactFilter.builder().sort("mostAppreciated").build();

        // when
        var firstPage = artifactRepository.search("default", 1L, filter, PageRequest.of(0, 2));
        var secondPage = artifactRepository.search("default", 1L, filter, PageRequest.of(1, 2));
        var thirdPage = artifactRepository.search("default", 1L, filter, PageRequest.of(2, 2));

        // then
        assertThat(firstPage.getContent()).extracting(ArtifactEntity::getTitle).containsExactly("First", "Second");
        assertThat(secondPage.getContent()).extracting(ArtifactEntity::getTitle).containsExactly("Third", "Fourth");
        assertThat(thirdPage.getContent()).extracting(ArtifactEntity::getTitle).containsExactly("Fifth");
    }

    @Test
    void stats_capturedThisMonthExcludesOlderArtifacts() {

        // given
        var old = standaloneArtifact("Old");
        jdbc.getJdbcTemplate().update("UPDATE artifact SET created_at = created_at - INTERVAL '2 months' WHERE id = ?",
                old);
        standaloneArtifact("Fresh");

        // when
        var stats = artifactRepository.stats("default", 1L);

        // then
        assertThat(stats.total()).isEqualTo(2L);
        assertThat(stats.capturedThisMonth()).isEqualTo(1L);
    }
}
