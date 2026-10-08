package dev.axonia.knowledge.internal;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.TagUsage;
import dev.axonia.TagUsageKind;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactTagWriterTest extends CoreJdbcTestSupport {

    private static final long COMMUNITY = 7L;
    private static final long OTHER_COMMUNITY = 8L;
    private static final String TITLE = "Resilience patterns";
    private static final String DESCRIPTION = "How we handle retries and timeouts";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ArtifactTagWriter writer;

    @BeforeEach
    void setUp() {

        jdbc.update(
                "INSERT INTO artifact_type (organization_id, label, created_at, updated_at, version) "
                        + "VALUES ('default', 'Notes', now(), now(), 0) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource());
        writer = new ArtifactTagWriter(jdbc);
    }

    @Test
    void renamingRetagsTheArtifactsOfTheCommunity() {

        var artifact = artifactWith(COMMUNITY, "kafak");

        writer.rename(COMMUNITY, "kafak", "kafka");

        assertThat(tagsOf(artifact)).containsExactly("kafka");
    }

    @Test
    void renamingFoldsAnArtifactThatAlreadyCarriesTheTarget() {

        var both = artifactWith(COMMUNITY, "kafak", "kafka");
        var only = artifactWith(COMMUNITY, "kafak");

        writer.rename(COMMUNITY, "kafak", "kafka");

        assertThat(tagsOf(both)).containsExactly("kafka");
        assertThat(tagsOf(only)).containsExactly("kafka");
    }

    @Test
    void renamingLeavesAnotherCommunitysArtifactsAlone() {

        var mine = artifactWith(COMMUNITY, "kafak");
        var theirs = artifactWith(OTHER_COMMUNITY, "kafak");

        writer.rename(COMMUNITY, "kafak", "kafka");

        assertThat(tagsOf(mine)).containsExactly("kafka");
        assertThat(tagsOf(theirs)).containsExactly("kafak");
    }

    @Test
    void removingStripsTheTagAndLeavesTheOthers() {

        var artifact = artifactWith(COMMUNITY, "misc", "kafka");

        writer.remove(COMMUNITY, "misc");

        assertThat(tagsOf(artifact)).containsExactly("kafka");
    }

    @Test
    void removingLeavesAnotherCommunitysArtifactsAlone() {

        var theirs = artifactWith(OTHER_COMMUNITY, "misc");

        writer.remove(COMMUNITY, "misc");

        assertThat(tagsOf(theirs)).containsExactly("misc");
    }

    @Test
    void renamingMakesTheArtifactFindableByTheNewNameAndNoLongerByTheOld() {

        var artifact = artifactWith(COMMUNITY, "kafak");

        writer.rename(COMMUNITY, "kafak", "kafka");

        assertThat(isFoundBySearchingFor(artifact, "kafka")).isTrue();
        assertThat(isFoundBySearchingFor(artifact, "kafak")).isFalse();
    }

    @Test
    void renamingLeavesTheTitleAndDescriptionSearchable() {

        var artifact = artifactWith(COMMUNITY, "kafak");

        writer.rename(COMMUNITY, "kafak", "kafka");

        assertThat(isFoundBySearchingFor(artifact, "resilience")).isTrue();
        assertThat(isFoundBySearchingFor(artifact, "timeouts")).isTrue();
    }

    @Test
    void renamingLeavesAnotherCommunitysSearchDocumentAlone() {

        var theirs = artifactWith(OTHER_COMMUNITY, "kafak");

        writer.rename(COMMUNITY, "kafak", "kafka");

        assertThat(isFoundBySearchingFor(theirs, "kafak")).isTrue();
        assertThat(isFoundBySearchingFor(theirs, "kafka")).isFalse();
    }

    @Test
    void removingMakesTheArtifactUnfindableByTheRetiredTag() {

        var artifact = artifactWith(COMMUNITY, "misc", "kafka");

        writer.remove(COMMUNITY, "misc");

        assertThat(isFoundBySearchingFor(artifact, "misc")).isFalse();
        assertThat(isFoundBySearchingFor(artifact, "kafka")).isTrue();
    }

    @Test
    void usageCountsTheArtifactsCarryingTheTag() {

        artifactWith(COMMUNITY, "kafka");
        artifactWith(COMMUNITY, "kafka");
        artifactWith(OTHER_COMMUNITY, "kafka");

        assertThat(writer.usage(COMMUNITY, "kafka")).containsExactly(new TagUsage(TagUsageKind.ARTIFACT, 2));
    }

    @Test
    void usageIsEmptyWhenNoArtifactCarriesTheTag() {

        assertThat(writer.usage(COMMUNITY, "kafka")).isEmpty();
    }

    @Test
    void theTagsInACommunityAreThoseItsArtifactsCarryEachOnce() {

        artifactWith(COMMUNITY, "kafka");
        artifactWith(COMMUNITY, "kafka");
        artifactWith(COMMUNITY, "helm");
        artifactWith(OTHER_COMMUNITY, "rust");

        assertThat(writer.tagsIn(COMMUNITY)).containsExactly("helm", "kafka");
    }

    /** Seeded the way {@code ArtifactService} seeds it: the document is the title, the description and the tags. */
    private long artifactWith(long communityId, String... tags) {

        var document = TITLE + " " + DESCRIPTION + " " + String.join(" ", tags);
        var artifactId = jdbc.queryForObject("""
                INSERT INTO artifact (organization_id, community_id, type_id, title, description, url,
                        contributor_user_id, status, search_document, created_at, updated_at, version)
                VALUES ('default', :communityId, (SELECT min(id) FROM artifact_type), :title, :description,
                        'https://example.com', 'alice', 'ACTIVE', :document, now(), now(), 0)
                RETURNING id
                """, new MapSqlParameterSource("communityId", communityId).addValue("title", TITLE)
                .addValue("description", DESCRIPTION).addValue("document", document), Long.class);

        for (var tag : tags) {
            jdbc.update("INSERT INTO artifact_tag (artifact_id, tag) VALUES (:artifactId, :tag)",
                    new MapSqlParameterSource("artifactId", artifactId).addValue("tag", tag));
        }
        return artifactId;
    }

    private List<String> tagsOf(long artifactId) {

        return jdbc.queryForList("SELECT tag FROM artifact_tag WHERE artifact_id = :artifactId",
                new MapSqlParameterSource("artifactId", artifactId), String.class);
    }

    /** The library's own free-text query, run against the one artifact. */
    private boolean isFoundBySearchingFor(long artifactId, String query) {

        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT search_vector @@ websearch_to_tsquery('english', :query)
                  FROM artifact
                 WHERE id = :artifactId
                """, new MapSqlParameterSource("artifactId", artifactId).addValue("query", query), Boolean.class));
    }
}
