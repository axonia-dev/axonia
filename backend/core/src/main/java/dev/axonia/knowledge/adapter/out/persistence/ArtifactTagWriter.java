package dev.axonia.knowledge.adapter.out.persistence;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.TagUsage;
import dev.axonia.community.api.TagUsageKind;
import dev.axonia.community.api.spi.CommunityTagWriter;
import lombok.RequiredArgsConstructor;

/**
 * The knowledge library's slice of a curator's rename or delete: the tags stamped on its artifacts.
 *
 * <p>
 * {@code artifact_tag} has no primary key, so a fold cannot lean on {@code ON CONFLICT} to collapse a duplicate. The
 * colliding row is deleted first, and only then is the survivor renamed.
 *
 * <p>
 * An artifact's tags are also part of its {@code search_document}, the text {@code search_vector} is generated from, so
 * a curator's write is only half done until that document is recomposed. {@link #REFRESH_DOCUMENT_SQL} composes it the
 * way {@code ArtifactService} does on contribute and on edit: title, description, tags, single-spaced and trimmed. The
 * two must stay in step. Tag order differs (alphabetical here, insertion order there) and is immaterial: a tsvector has
 * no order, and no reader shows the document.
 */
@Component
@RequiredArgsConstructor
class ArtifactTagWriter implements CommunityTagWriter {

    private static final String COUNT_SQL = """
            SELECT count(DISTINCT t.artifact_id)
              FROM artifact_tag t
              JOIN artifact a ON a.id = t.artifact_id
             WHERE a.community_id = :communityId AND t.tag = :tag
            """;

    private static final String TAGS_SQL = """
            SELECT DISTINCT t.tag
              FROM artifact_tag t
              JOIN artifact a ON a.id = t.artifact_id
             WHERE a.community_id = :communityId
             ORDER BY t.tag
            """;

    private static final String DELETE_COLLIDING_SQL = """
            DELETE FROM artifact_tag t
             USING artifact a
             WHERE t.artifact_id = a.id
               AND a.community_id = :communityId
               AND t.tag = :from
               AND EXISTS (SELECT 1 FROM artifact_tag o WHERE o.artifact_id = t.artifact_id AND o.tag = :to)
            """;

    private static final String RENAME_SQL = """
            UPDATE artifact_tag t
               SET tag = :to
              FROM artifact a
             WHERE t.artifact_id = a.id AND a.community_id = :communityId AND t.tag = :from
            """;

    private static final String REMOVE_SQL = """
            DELETE FROM artifact_tag t
             USING artifact a
             WHERE t.artifact_id = a.id AND a.community_id = :communityId AND t.tag = :tag
            """;

    private static final String TAGGED_ARTIFACTS_SQL = """
            SELECT DISTINCT t.artifact_id
              FROM artifact_tag t
              JOIN artifact a ON a.id = t.artifact_id
             WHERE a.community_id = :communityId AND t.tag = :tag
            """;

    private static final String REFRESH_DOCUMENT_SQL = """
            UPDATE artifact a
               SET search_document = btrim(a.title || ' ' || a.description || ' '
                       || coalesce((SELECT string_agg(t.tag, ' ' ORDER BY t.tag)
                                      FROM artifact_tag t
                                     WHERE t.artifact_id = a.id), ''))
             WHERE a.id IN (:artifactIds)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public List<String> tagsIn(long communityId) {

        return jdbc.queryForList(TAGS_SQL, new MapSqlParameterSource("communityId", communityId), String.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagUsage> usage(long communityId, String tag) {

        var params = new MapSqlParameterSource("communityId", communityId).addValue("tag", tag);
        var count = jdbc.queryForObject(COUNT_SQL, params, Integer.class);
        return count == null || count == 0 ? List.of() : List.of(new TagUsage(TagUsageKind.ARTIFACT, count));
    }

    @Override
    public void rename(long communityId, String from, String to) {

        var params = new MapSqlParameterSource("communityId", communityId).addValue("from", from).addValue("to", to);
        var touched = artifactsTagged(communityId, from);
        jdbc.update(DELETE_COLLIDING_SQL, params);
        jdbc.update(RENAME_SQL, params);
        refreshSearchDocuments(touched);
    }

    @Override
    public void remove(long communityId, String tag) {

        var params = new MapSqlParameterSource("communityId", communityId).addValue("tag", tag);
        var touched = artifactsTagged(communityId, tag);
        jdbc.update(REMOVE_SQL, params);
        refreshSearchDocuments(touched);
    }

    /** Read before the write: once the tag is gone, the artifacts that carried it can no longer be found by it. */
    private List<Long> artifactsTagged(long communityId, String tag) {

        var params = new MapSqlParameterSource("communityId", communityId).addValue("tag", tag);
        return jdbc.queryForList(TAGGED_ARTIFACTS_SQL, params, Long.class);
    }

    private void refreshSearchDocuments(List<Long> artifactIds) {

        if (artifactIds.isEmpty()) {
            return;
        }
        jdbc.update(REFRESH_DOCUMENT_SQL, new MapSqlParameterSource("artifactIds", artifactIds));
    }
}
