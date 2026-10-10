package dev.axonia.knowledge.adapter.out.privacy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintTestSupport;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactRepository;
import dev.axonia.shared.api.personaldata.MemberErasure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeMemberDataEraserTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String OTHER_ORG = "other-org";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";
    private static final String SURROGATE = "erased:test-surrogate";
    private static final Instant ERASED_AT = Instant.parse("2026-07-27T10:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private KnowledgeMemberDataEraser eraser;

    @Autowired
    private ArtifactRepository artifacts;

    @Test
    void anonymisesContributorshipAndAttachmentsWithoutChangingAnyCount() {

        var targetArtifact = seedArtifact(ORG, TARGET);
        var neighbourArtifact = seedArtifact(ORG, NEIGHBOUR);
        var otherOrgArtifact = seedArtifact(OTHER_ORG, TARGET);
        seedAttachment(targetArtifact, ORG, TARGET);
        seedAttachment(neighbourArtifact, ORG, NEIGHBOUR);
        // same user id, attachment and parent agree on the other organization - untouched baseline
        seedAttachment(otherOrgArtifact, OTHER_ORG, TARGET);
        // deliberately disagreeing: this attachment's own organization_id is the org being erased, even though its
        // parent artifact sits in another tenant - nothing in the schema forces the two to agree, and the erasure
        // must go by the attachment's own column, not its parent's
        seedAttachment(otherOrgArtifact, ORG, TARGET);

        var artifactsBefore = countArtifacts();
        var attachmentsBefore = countAttachmentsInOrg();

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        assertThat(counts.anonymised()).isEqualTo(3);
        // the artifact and attachment totals are the point: a row that vanishes is a number that silently changes
        // for everyone else
        assertThat(countArtifacts()).isEqualTo(artifactsBefore);
        assertThat(countAttachmentsInOrg()).isEqualTo(attachmentsBefore);
        assertThat(artifactContributor(targetArtifact)).isEqualTo(SURROGATE);
        assertThat(artifactContributor(neighbourArtifact)).isEqualTo(NEIGHBOUR);
        // the other-organization artifact is untouched: same user id, wrong tenant
        assertThat(artifactContributor(otherOrgArtifact)).isEqualTo(TARGET);
        assertThat(attachmentAuthor(targetArtifact, ORG)).isEqualTo(SURROGATE);
        assertThat(attachmentAuthor(neighbourArtifact, ORG)).isEqualTo(NEIGHBOUR);
        // the other-organization attachment is untouched: same user id, wrong tenant, and parent agrees
        assertThat(attachmentAuthor(otherOrgArtifact, OTHER_ORG)).isEqualTo(TARGET);
        // the disagreeing attachment is reached: its own organization_id is the org being erased, regardless of
        // where its parent artifact lives
        assertThat(attachmentAuthor(otherOrgArtifact, ORG)).isEqualTo(SURROGATE);
    }

    /**
     * The failure this guards against is a legal erasure silently undone. {@code ArtifactEntity} carries
     * {@code @Version}, so a request that loaded an artifact before the erasure holds a version the erasure's raw
     * UPDATE used to leave untouched - its {@code WHERE version = ?} predicate still matched afterwards, and Spring
     * Data JDBC wrote the whole entity back, pre-erasure contributor and all. Bumping the version in the same statement
     * turns that silent resurrection into the optimistic-lock failure it always should have been.
     */
    @Test
    void aRequestHoldingThePreErasureRowCannotWriteTheErasedSubjectBack() {

        var artifactId = seedArtifact(ORG, TARGET);
        var loadedBeforeErasure = artifacts.findByOrganizationIdAndId(ORG, artifactId).orElseThrow();

        eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        assertThatThrownBy(() -> artifacts.save(loadedBeforeErasure))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(artifactContributor(artifactId)).isEqualTo(SURROGATE);
    }

    /** The stamp comes from the erasure's own instant, never SQL {@code now()}. */
    @Test
    void everyAnonymisedRowIsStampedWithTheErasuresOwnInstant() {

        var artifactId = seedArtifact(ORG, TARGET);
        seedAttachment(artifactId, ORG, TARGET);

        eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        assertThat(artifacts.findByOrganizationIdAndId(ORG, artifactId).orElseThrow().getUpdatedAt())
                .isEqualTo(ERASED_AT);
        assertThat(attachmentUpdatedAt(artifactId, ORG)).isEqualTo(Timestamp.from(ERASED_AT));
    }

    /** An opening carries no copy of the contributor, so erasing it is a plain delete, not an anonymise. */
    @Test
    void deletesTheTargetsOwnOpeningsAndLeavesEveryoneElsesAlone() {

        var artifactId = seedArtifact(ORG, NEIGHBOUR);
        seedOpening(artifactId, ORG, TARGET);
        seedOpening(artifactId, ORG, NEIGHBOUR);

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        assertThat(counts.deleted()).isEqualTo(1);
        assertThat(openingExists(artifactId, ORG, TARGET)).isFalse();
        assertThat(openingExists(artifactId, ORG, NEIGHBOUR)).isTrue();
    }

    private void seedOpening(Long artifactId, String org, String memberUserId) {

        jdbc.update("""
                INSERT INTO artifact_opening (organization_id, community_id, artifact_id, member_user_id,
                                              opened_on, created_at)
                VALUES (:org, 1, :artifactId, :memberUserId, DATE '2026-07-27', now())
                """, Map.of("org", org, "artifactId", artifactId, "memberUserId", memberUserId));
    }

    private boolean openingExists(Long artifactId, String org, String memberUserId) {

        var count = jdbc.queryForObject(
                "SELECT count(*) FROM artifact_opening WHERE artifact_id = :id AND organization_id = :org "
                        + "AND member_user_id = :memberUserId",
                Map.of("id", artifactId, "org", org, "memberUserId", memberUserId), Long.class);
        return count > 0;
    }

    private Timestamp attachmentUpdatedAt(Long artifactId, String org) {

        return jdbc.queryForObject(
                "SELECT updated_at FROM artifact_attachment WHERE artifact_id = :id AND organization_id = :org",
                Map.of("id", artifactId, "org", org), Timestamp.class);
    }

    private Long seedArtifact(String org, String contributor) {

        return jdbc.queryForObject(
                "INSERT INTO artifact (organization_id, community_id, type_id, title, description, url, "
                        + "contributor_user_id, status, search_document, created_at, updated_at, version) "
                        + "VALUES (:org, 1, 1, 'Eraser Test Artifact', '', 'https://example.test/artifact', "
                        + ":contributor, 'PUBLISHED', 'eraser test artifact', now(), now(), 0) RETURNING id",
                Map.of("org", org, "contributor", contributor), Long.class);
    }

    /**
     * Seeds an attachment with an explicit organization_id of its own, independent of its parent artifact's - nothing
     * in the schema forces the two to agree, and the eraser must be scoped by the attachment's own column.
     */
    private void seedAttachment(Long artifactId, String org, String attachedBy) {

        jdbc.update(
                "INSERT INTO artifact_attachment (organization_id, artifact_id, session_id, segment_id, "
                        + "attached_by_user_id, created_at, updated_at, version) VALUES (:org, :artifactId, 1, 1, "
                        + ":attachedBy, now(), now(), 0)",
                Map.of("org", org, "artifactId", artifactId, "attachedBy", attachedBy));
    }

    private long countArtifacts() {

        return jdbc.queryForObject("SELECT count(*) FROM artifact WHERE organization_id = :org", Map.of("org", ORG),
                Long.class);
    }

    /**
     * Counts by the attachment's own organization_id, independently of the artifact join the eraser no longer relies on
     * - an oracle that shares the production query's scoping could not have caught the miss this test guards against.
     */
    private long countAttachmentsInOrg() {

        return jdbc.queryForObject("SELECT count(*) FROM artifact_attachment WHERE organization_id = :org",
                Map.of("org", ORG), Long.class);
    }

    private String artifactContributor(Long artifactId) {

        return jdbc.queryForObject("SELECT contributor_user_id FROM artifact WHERE id = :id", Map.of("id", artifactId),
                String.class);
    }

    private String attachmentAuthor(Long artifactId, String org) {

        return jdbc.queryForObject(
                "SELECT attached_by_user_id FROM artifact_attachment WHERE artifact_id = :id AND organization_id = :org",
                Map.of("id", artifactId, "org", org), String.class);
    }
}
