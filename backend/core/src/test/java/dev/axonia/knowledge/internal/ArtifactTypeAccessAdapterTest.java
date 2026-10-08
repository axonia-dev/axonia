package dev.axonia.knowledge.internal;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.recognition.ArtifactBadge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class ArtifactTypeAccessAdapterTest extends CoreJdbcTestSupport {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ArtifactTypeAccessAdapter sut;

    @Test
    void artifactTypes_returnsTypeBadgePerArtifact() {

        sut = new ArtifactTypeAccessAdapter(jdbc);
        var typeId = seedArtifactType("RFC", "file-text", "#abcdef");
        var artifactId = seedArtifact(typeId, "Event sourcing RFC");

        assertThat(sut.artifactTypes(List.of(artifactId)))
                .containsOnly(entry(artifactId, new ArtifactBadge(typeId, "RFC", "file-text", "#abcdef")));
    }

    @Test
    void artifactTypes_emptyInput_returnsEmptyMap() {

        sut = new ArtifactTypeAccessAdapter(jdbc);

        assertThat(sut.artifactTypes(List.of())).isEmpty();
    }

    private Long seedArtifactType(String label, String icon, String accentColor) {

        return jdbc.queryForObject(
                "INSERT INTO artifact_type (organization_id, label, created_at, updated_at, version, icon, accent_color) "
                        + "VALUES ('default', :label, now(), now(), 0, :icon, :accentColor) RETURNING id",
                new MapSqlParameterSource("label", label).addValue("icon", icon).addValue("accentColor", accentColor),
                Long.class);
    }

    private Long seedArtifact(Long typeId, String title) {

        return jdbc.queryForObject("""
                INSERT INTO artifact (organization_id, community_id, type_id, title, description, url,
                        contributor_user_id, status, search_document, created_at, updated_at, version)
                VALUES ('default', 1, :typeId, :title, 'd', 'https://example.com', 'alice', 'ACTIVE', 'doc',
                        now(), now(), 0) RETURNING id
                """, new MapSqlParameterSource("typeId", typeId).addValue("title", title), Long.class);
    }
}
