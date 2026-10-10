package dev.axonia.knowledge.adapter.out.privacy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.PurgerTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeDataPurgerTest extends PurgerTestSupport {

    private static final String ORG = "default";
    private static final String OTHER_ORG = "other-org";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private KnowledgeDataPurger purger;

    @Test
    void purgeEmptiesEveryKnowledgeTableForTheOrganization() {

        // given - an artifact with a type, one attachment and one tag riding it
        seedKnowledgeGraph();

        // when
        purger.purge(ORG);

        // then - artifact/artifact_type are org-scoped; attachment/tag cascade from artifact so are asserted overall
        assertThat(count("artifact")).isZero();
        assertThat(count("artifact_type")).isZero();
        assertThat(countAll("artifact_attachment")).isZero();
        assertThat(countAll("artifact_tag")).isZero();
    }

    @Test
    void purgeRemovesOnlyTheGivenOrganizationsOpenings() {

        // given - one artifact and one opening in each of two organizations
        var artifactId = seedMinimalArtifact(ORG);
        var otherArtifactId = seedMinimalArtifact(OTHER_ORG);
        seedOpening(ORG, artifactId);
        seedOpening(OTHER_ORG, otherArtifactId);

        // when
        purger.purge(ORG);

        // then
        assertThat(countOpenings(ORG)).isZero();
        assertThat(countOpenings(OTHER_ORG)).isEqualTo(1);
    }

    /**
     * Seeds one community, one artifact_type, one artifact riding it, plus one artifact_attachment and one artifact_tag
     * riding the artifact, all for org "default".
     */
    private void seedKnowledgeGraph() {

        var kindId = jdbc.queryForObject(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES (:org, 'Purger Test Guild', now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);

        var communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, 'purger-test-community', 'Purger Test Community', "
                        + ":kindId, '', 'ACTIVE', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "kindId", kindId), Long.class);

        var typeId = jdbc.queryForObject(
                "INSERT INTO artifact_type (organization_id, label, created_at, updated_at, version) "
                        + "VALUES (:org, 'Purger Test Type', now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);

        var artifactId = jdbc.queryForObject(
                "INSERT INTO artifact (organization_id, community_id, type_id, title, description, url, "
                        + "contributor_user_id, status, search_document, created_at, updated_at, version) "
                        + "VALUES (:org, :communityId, :typeId, 'Purger Test Artifact', '', "
                        + "'https://example.test/artifact', 'contributor-user', 'PUBLISHED', 'purger test artifact', "
                        + "now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "communityId", communityId, "typeId", typeId), Long.class);

        jdbc.update("INSERT INTO artifact_attachment (organization_id, artifact_id, session_id, segment_id, "
                + "attached_by_user_id, created_at, updated_at, version) VALUES (:org, :artifactId, 1, 1, "
                + "'attacher-user', now(), now(), 0)", Map.of("org", ORG, "artifactId", artifactId));

        jdbc.update("INSERT INTO artifact_tag (artifact_id, tag) VALUES (:artifactId, 'purger-test-tag')",
                Map.of("artifactId", artifactId));
    }

    private Long seedMinimalArtifact(String org) {

        return jdbc.queryForObject(
                "INSERT INTO artifact (organization_id, community_id, type_id, title, description, url, "
                        + "contributor_user_id, status, search_document, created_at, updated_at, version) "
                        + "VALUES (:org, 1, 1, 'Purger Opening Test Artifact', '', 'https://example.test/artifact', "
                        + "'contributor-user', 'ACTIVE', 'purger opening test artifact', now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", org), Long.class);
    }

    private void seedOpening(String org, Long artifactId) {

        jdbc.update("""
                INSERT INTO artifact_opening (organization_id, community_id, artifact_id, member_user_id,
                                              opened_on, created_at)
                VALUES (:org, 1, :artifactId, 'opener-user', DATE '2026-07-27', now())
                """, Map.of("org", org, "artifactId", artifactId));
    }

    private long countOpenings(String org) {

        return jdbc.queryForObject("SELECT count(*) FROM artifact_opening WHERE organization_id = :org",
                Map.of("org", org), Long.class);
    }

    private long count(String table) {

        return switch (table) {
            case "artifact" -> jdbc.queryForObject("SELECT count(*) FROM artifact WHERE organization_id = :org",
                    Map.of("org", ORG), Long.class);
            case "artifact_type" -> jdbc.queryForObject(
                    "SELECT count(*) FROM artifact_type WHERE organization_id = :org", Map.of("org", ORG), Long.class);
            default -> throw new IllegalArgumentException("Unexpected org-scoped table: " + table);
        };
    }

    private long countAll(String table) {

        return switch (table) {
            case "artifact_attachment" ->
                jdbc.queryForObject("SELECT count(*) FROM artifact_attachment", Map.of(), Long.class);
            case "artifact_tag" -> jdbc.queryForObject("SELECT count(*) FROM artifact_tag", Map.of(), Long.class);
            default -> throw new IllegalArgumentException("Unexpected table: " + table);
        };
    }
}
