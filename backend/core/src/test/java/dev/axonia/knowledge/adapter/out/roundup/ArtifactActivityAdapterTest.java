package dev.axonia.knowledge.adapter.out.roundup;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.roundup.api.spi.ArtifactActivity.CapturedArtifact;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactActivityAdapterTest extends CoreJdbcTestSupport {

    /**
     * Its own organization, never the shared "default": this port answers for a whole organization at once, so a row
     * another test committed there would be part of the answer.
     */
    private static final String ORGANIZATION_ID = "roundup-artifacts";
    private static final String OTHER_ORGANIZATION_ID = "roundup-artifacts-other";
    private static final Long SESSION_ID = 100L;
    private static final Long OTHER_SESSION_ID = 101L;
    private static final Long COMMUNITY_ID = 1L;
    private static final Instant CREATED_AT = Instant.parse("2026-05-12T17:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ArtifactActivityAdapter sut;
    private Long recordingTypeId;

    @BeforeEach
    void setUp() {

        sut = new ArtifactActivityAdapter(jdbc);
        recordingTypeId = insertArtifactType("Recording");
    }

    @Test
    void anArtifactIsCapturedByTheSessionItIsAttachedTo_notByWhenItWasWrittenDown() {

        var artifact = insertArtifact("Recording Title", "https://example.test/rec", recordingTypeId,
                Instant.parse("2026-08-02T00:00:00Z"));
        attach(artifact, SESSION_ID, null);

        var captured = sut.capturedForSessions(ORGANIZATION_ID, Map.of(SESSION_ID, COMMUNITY_ID));

        assertThat(captured).containsExactly(
                new CapturedArtifact(SESSION_ID, "Recording Title", "https://example.test/rec", "Recording"));
    }

    @Test
    void oneArtifactOnTwoSessionsIsAnswerdOncePerSession() {

        var artifact = insertArtifact("Recording Title", "https://example.test/rec", recordingTypeId, CREATED_AT);
        attach(artifact, SESSION_ID, null);
        attach(artifact, OTHER_SESSION_ID, null);

        assertThat(sut.capturedForSessions(ORGANIZATION_ID,
                Map.of(SESSION_ID, COMMUNITY_ID, OTHER_SESSION_ID, COMMUNITY_ID)))
                .extracting(CapturedArtifact::sessionId).containsExactly(SESSION_ID, OTHER_SESSION_ID);
    }

    @Test
    void aQuarterThatHeldNothingAsksTheDatabaseNothing() {

        assertThat(sut.capturedForSessions(ORGANIZATION_ID, Map.of())).isEmpty();
    }

    @Test
    void anArtifactWhoseTypeRowIsGoneHasANullTypeLabel() {

        var goneTypeId = 999_999_999L;
        var artifact = insertArtifact("Orphaned Notes", "https://example.test/notes", goneTypeId, CREATED_AT);
        attach(artifact, SESSION_ID, null);

        var captured = sut.capturedForSessions(ORGANIZATION_ID, Map.of(SESSION_ID, COMMUNITY_ID));

        assertThat(captured).containsExactly(
                new CapturedArtifact(SESSION_ID, "Orphaned Notes", "https://example.test/notes", null));
    }

    /**
     * Archiving is this product's soft delete for an artifact: it leaves the attachment row alone and flips
     * {@code artifact.status}, so the join still finds it. Every other read of the table filters it out, and this port
     * hands a mail a title and a live link rather than a count, so it must too.
     */
    @Test
    void anArchivedArtifactIsNeitherCountedNorLinked() {

        var live = insertArtifact("Live Recording", "https://example.test/live", recordingTypeId, CREATED_AT);
        var archived = insertArtifact("Taken Down", "https://example.test/gone", recordingTypeId, CREATED_AT,
                ORGANIZATION_ID, COMMUNITY_ID, "ARCHIVED");
        attach(live, SESSION_ID, null);
        attach(archived, SESSION_ID, null);

        var captured = sut.capturedForSessions(ORGANIZATION_ID, Map.of(SESSION_ID, COMMUNITY_ID));

        assertThat(captured).extracting(CapturedArtifact::title).containsExactly("Live Recording");
    }

    /**
     * {@code uq_attachment} is unique on (organization, artifact, session, segment) with NULLS NOT DISTINCT, so one
     * shared deck attached to two of a session's segments is two rows carrying the same session, title and url. The row
     * has no segment on it, so the letter would print the same link twice under one session.
     */
    @Test
    void anArtifactAttachedTwiceToOneSessionIsOneResourceUnderThatSession() {

        var artifact = insertArtifact("Shared Deck", "https://example.test/deck", recordingTypeId, CREATED_AT);
        attach(artifact, SESSION_ID, 500L);
        attach(artifact, SESSION_ID, 501L);

        var captured = sut.capturedForSessions(ORGANIZATION_ID, Map.of(SESSION_ID, COMMUNITY_ID));

        assertThat(captured).containsExactly(
                new CapturedArtifact(SESSION_ID, "Shared Deck", "https://example.test/deck", "Recording"));
    }

    /**
     * The session ids arrive from the caller rather than from a query, so the organization predicate is this port's
     * only tenant boundary - and no owner-connected test can lean on RLS to make up for its absence.
     */
    @Test
    void anotherOrganizationsArtifactsAreNeverAnswered() {

        var theirType = insertArtifactType("Recording", OTHER_ORGANIZATION_ID);
        var theirs = insertArtifact("Theirs", "https://example.test/theirs", theirType, CREATED_AT,
                OTHER_ORGANIZATION_ID, COMMUNITY_ID, "ACTIVE");
        attach(theirs, SESSION_ID, null, OTHER_ORGANIZATION_ID);

        assertThat(sut.capturedForSessions(ORGANIZATION_ID, Map.of(SESSION_ID, COMMUNITY_ID))).isEmpty();
    }

    /**
     * A third read surface: a resource attached to a session under a DIFFERENT community than the artifact's own used
     * to still print in the quarterly letter, title and live link both. {@code sessionCommunityIds} carries the
     * community {@code RoundupFactsAssembler} already knows the session belongs to (7, here); the artifact was planted
     * under community 2, so the row is excluded even though the attachment and the session's organization both match.
     */
    @Test
    void anArtifactPlantedUnderADifferentCommunityThanItsSessionIsExcluded() {

        var plantedCommunityId = 2L;
        var sessionsRealCommunityId = 7L;
        var planted = insertArtifact("Planted", "https://example.test/planted", recordingTypeId, CREATED_AT,
                ORGANIZATION_ID, plantedCommunityId, "ACTIVE");
        attach(planted, SESSION_ID, null);

        var captured = sut.capturedForSessions(ORGANIZATION_ID, Map.of(SESSION_ID, sessionsRealCommunityId));

        assertThat(captured).isEmpty();
    }

    private Long insertArtifactType(String label) {

        return insertArtifactType(label, ORGANIZATION_ID);
    }

    private Long insertArtifactType(String label, String organizationId) {

        return jdbc.queryForObject("""
                INSERT INTO artifact_type (organization_id, label, created_at, updated_at, version)
                VALUES (:org, :label, now(), now(), 0) RETURNING id
                """, new MapSqlParameterSource("org", organizationId).addValue("label", label), Long.class);
    }

    private Long insertArtifact(String title, String url, Long typeId, Instant createdAt) {

        return insertArtifact(title, url, typeId, createdAt, ORGANIZATION_ID, COMMUNITY_ID, "ACTIVE");
    }

    private Long insertArtifact(String title, String url, Long typeId, Instant createdAt, String organizationId,
            Long communityId, String status) {

        return jdbc.queryForObject("""
                INSERT INTO artifact (organization_id, community_id, type_id, title, description, url,
                        contributor_user_id, status, search_document, created_at, updated_at, version)
                VALUES (:org, :communityId, :typeId, :title, 'd', :url, 'alice', :status, 'doc', :createdAt, now(), 0)
                RETURNING id
                """,
                new MapSqlParameterSource("org", organizationId).addValue("communityId", communityId)
                        .addValue("typeId", typeId).addValue("title", title).addValue("url", url)
                        .addValue("status", status).addValue("createdAt", Timestamp.from(createdAt)),
                Long.class);
    }

    private void attach(Long artifactId, Long sessionId, Long segmentId) {

        attach(artifactId, sessionId, segmentId, ORGANIZATION_ID);
    }

    private void attach(Long artifactId, Long sessionId, Long segmentId, String organizationId) {

        jdbc.update("""
                INSERT INTO artifact_attachment (organization_id, artifact_id, session_id, segment_id,
                        attached_by_user_id, created_at, updated_at, version)
                VALUES (:org, :artifactId, :sessionId, :segmentId, 'alice', now(), now(), 0)
                """, new MapSqlParameterSource("org", organizationId).addValue("artifactId", artifactId)
                .addValue("sessionId", sessionId).addValue("segmentId", segmentId));
    }
}
