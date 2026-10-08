package dev.axonia.knowledge;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class ArtifactTitleReaderTest extends CoreJdbcTestSupport {

    /** No row reaches this far, so it stands for an artifact the caller's reference outlived. */
    private static final long GONE = 999_000L;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ArtifactTitleReader sut;

    @BeforeEach
    void createReader() {

        sut = new ArtifactTitleReader(jdbc, () -> "default");
    }

    @Test
    void titlesByIds_returnsTitlePerArtifactId() {

        var communityId = seedCommunity();
        var typeId = seedArtifactType();
        var first = seedArtifact(communityId, typeId, "Kafka playbook");
        var second = seedArtifact(communityId, typeId, "Kubernetes runbook");

        assertThat(sut.titlesByIds(List.of(first, second))).containsOnly(entry(first, "Kafka playbook"),
                entry(second, "Kubernetes runbook"));
    }

    @Test
    void titlesByIds_areScopedToTheCallersOrganization() {

        var communityId = seedCommunity();
        var typeId = seedArtifactType();
        var mine = seedArtifact("default", communityId, typeId, "Kafka playbook");
        var theirs = seedArtifact("other-org", communityId, typeId, "Their playbook");

        assertThat(sut.titlesByIds(List.of(mine, theirs))).containsOnly(entry(mine, "Kafka playbook"));
    }

    @Test
    void anIdThatNoLongerResolvesIsAbsentRatherThanAThrow() {

        assertThat(sut.titlesByIds(List.of(GONE))).isEmpty();
    }

    @Test
    void emptyInput_returnsEmptyMap() {

        assertThat(sut.titlesByIds(List.of())).isEmpty();
    }

    private Long seedCommunity() {

        jdbc.update(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES ('default', 'ArtifactTitleKind', now(), now(), 0) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource());
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, updated_at, version) "
                        + "SELECT 'default', 'artifact-title-' || nextval('communities_id_seq'), 'AT', k.id, '', 'ACTIVE', now(), now(), 0 "
                        + "FROM community_kind k WHERE k.organization_id='default' AND k.label='ArtifactTitleKind' RETURNING id",
                new MapSqlParameterSource(), Long.class);
    }

    private Long seedArtifactType() {

        return jdbc.queryForObject(
                "INSERT INTO artifact_type (organization_id, label, created_at, updated_at, version) "
                        + "VALUES ('default', 'ArtifactTitleType', now(), now(), 0) RETURNING id",
                new MapSqlParameterSource(), Long.class);
    }

    private Long seedArtifact(Long communityId, Long typeId, String title) {

        return seedArtifact("default", communityId, typeId, title);
    }

    private Long seedArtifact(String organizationId, Long communityId, Long typeId, String title) {

        return jdbc.queryForObject(
                "INSERT INTO artifact (organization_id, community_id, type_id, title, description, url, "
                        + "contributor_user_id, status, search_document, love_count, created_at, updated_at, version) "
                        + "VALUES (:org, :communityId, :typeId, :title, '', 'https://example.test', "
                        + "'artifact-title-contributor', 'ACTIVE', :title, 0, now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("org", organizationId).addValue("communityId", communityId)
                        .addValue("typeId", typeId).addValue("title", title),
                Long.class);
    }
}
