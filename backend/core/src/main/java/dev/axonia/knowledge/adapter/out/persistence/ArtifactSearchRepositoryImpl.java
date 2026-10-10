package dev.axonia.knowledge.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.knowledge.api.ArtifactFilter;
import dev.axonia.knowledge.domain.ArtifactSession;
import dev.axonia.knowledge.domain.LibraryStats;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class ArtifactSearchRepositoryImpl implements ArtifactSearchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    @SuppressWarnings({"PMD.NcssCount", "PMD.CyclomaticComplexity", "PMD.NPathComplexity"})
    @Override
    public Page<ArtifactEntity> search(String organizationId, Long communityId, ArtifactFilter filter,
            Pageable pageable) {

        var where = new StringBuilder(" FROM artifact a WHERE a.organization_id = :organizationId"
                + " AND a.community_id = :communityId AND a.status = 'ACTIVE'");
        var params = new MapSqlParameterSource().addValue("organizationId", organizationId).addValue("communityId",
                communityId);

        var ranked = filter.q() != null && !filter.q().isBlank();
        if (ranked) {
            where.append(" AND a.search_vector @@ websearch_to_tsquery('english', :q)");
            params.addValue("q", filter.q());
        }
        if (filter.typeId() != null) {
            where.append(" AND a.type_id = :typeId");
            params.addValue("typeId", filter.typeId());
        }
        if (filter.contributor() != null && !filter.contributor().isBlank()) {
            where.append(" AND a.contributor_user_id = :contributor");
            params.addValue("contributor", filter.contributor());
        }
        if (filter.from() != null) {
            where.append(" AND a.created_at >= :from");
            params.addValue("from", Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            where.append(" AND a.created_at <= :to");
            params.addValue("to", Timestamp.from(filter.to()));
        }
        if (filter.tag() != null && !filter.tag().isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM artifact_tag t WHERE t.artifact_id = a.id AND t.tag = :tag)");
            params.addValue("tag", filter.tag());
        }
        appendSessionFilter(filter, where, params);

        var total = jdbc.queryForObject("SELECT count(*)" + where, params, Long.class);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        var order = orderClause(filter, ranked);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());

        var ids = jdbc.queryForList("SELECT a.id" + where + order + " LIMIT :limit OFFSET :offset", params, Long.class);
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        return new PageImpl<>(hydrate(ids, organizationId, communityId), pageable, total);
    }

    /**
     * One roots query plus one tags query for the whole page, never one pair per id.
     * {@code JdbcAggregateTemplate.findAllById} batches the root row through its own {@code IN}, but still loads each
     * row's {@code tags} collection with its own {@code WHERE artifact_id = ?} - measured as 20 separate statements for
     * a 20-row page, not the single batched query its aggregate-loading machinery seems to promise. So both reads here
     * are hand-written, matching the id and count queries above, and the ranked order the id query already produced is
     * replayed onto them: {@code IN} does not preserve it.
     *
     * <p>
     * {@code ROOTS_SQL} repeats the tenant/community/status predicate every other query in this file carries, even
     * though RLS already enforces it - the file's established convention, deliberately redundant. {@code ids} is always
     * the output of the immediately preceding scoped query in {@link #search}, so this is not exploitable today, but
     * nothing here can tell "correctly scoped" from "coincidentally correct": RLS is not exercised by this test suite
     * (Testcontainers connects as the schema owner, who bypasses it by ownership), so it has to be right by
     * construction rather than by testing.
     */
    private List<ArtifactEntity> hydrate(List<Long> ids, String organizationId, Long communityId) {

        var rootParams = new MapSqlParameterSource().addValue("ids", ids).addValue("organizationId", organizationId)
                .addValue("communityId", communityId);
        var roots = jdbc.query(ROOTS_SQL, rootParams, ArtifactSearchRepositoryImpl::mapRoot);
        var tagsByArtifactId = tagsByArtifactId(ids);

        var byId = new HashMap<Long, ArtifactEntity>();
        for (var root : roots) {
            byId.put(root.getId(),
                    root.toBuilder().tags(tagsByArtifactId.getOrDefault(root.getId(), Set.of())).build());
        }
        return ids.stream().map(byId::get).filter(a -> a != null).toList();
    }

    /**
     * Not scoped by {@code organization_id}, unlike {@code ROOTS_SQL}: {@code artifact_tag} has no such column and, per
     * {@code V1__baseline.sql}, no RLS policy of its own - it relies structurally on the FK-cascaded
     * {@code artifact_id} for isolation, the same way the tag-filter {@code EXISTS} clause above correlates through the
     * already-scoped {@code a} alias rather than repeating the tenant predicate itself. This is considered, not missed.
     */
    private Map<Long, Set<ArtifactTagRef>> tagsByArtifactId(List<Long> ids) {

        var params = new MapSqlParameterSource().addValue("ids", ids);
        var tagsByArtifactId = new HashMap<Long, Set<ArtifactTagRef>>();
        jdbc.query(TAGS_SQL, params, rs -> {
            tagsByArtifactId.computeIfAbsent(rs.getLong("artifact_id"), id -> new HashSet<>())
                    .add(ArtifactTagRef.builder().tag(rs.getString("tag")).build());
        });
        return tagsByArtifactId;
    }

    private static ArtifactEntity mapRoot(ResultSet rs, int rowNum) throws SQLException {

        return ArtifactEntity.builder().id(rs.getLong("id")).organizationId(rs.getString("organization_id"))
                .communityId(rs.getLong("community_id")).typeId(rs.getLong("type_id")).title(rs.getString("title"))
                .description(rs.getString("description")).url(rs.getString("url"))
                .contributorUserId(rs.getString("contributor_user_id"))
                .provenanceSessionId(rs.getObject("provenance_session_id", Long.class))
                .provenanceSegmentId(rs.getObject("provenance_segment_id", Long.class))
                .provenanceLabel(rs.getString("provenance_label")).status(rs.getString("status"))
                .searchDocument(rs.getString("search_document")).loveCount(rs.getInt("love_count"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant()).version(rs.getLong("version")).build();
    }

    private static final String ROOTS_SQL = """
            SELECT id, organization_id, community_id, type_id, title, description, url, contributor_user_id,
                   provenance_session_id, provenance_segment_id, provenance_label, status, search_document,
                   love_count, created_at, updated_at, version
              FROM artifact
             WHERE id IN (:ids) AND organization_id = :organizationId AND community_id = :communityId
               AND status = 'ACTIVE'
            """;

    private static final String TAGS_SQL = "SELECT artifact_id, tag FROM artifact_tag WHERE artifact_id IN (:ids)";

    private String orderClause(ArtifactFilter filter, boolean ranked) {

        if ("mostAppreciated".equals(filter.sort())) {
            return " ORDER BY a.love_count DESC, a.created_at DESC";
        }
        if (ranked) {
            return " ORDER BY ts_rank(a.search_vector, websearch_to_tsquery('english', :q)) DESC, a.created_at DESC";
        }
        return " ORDER BY a.created_at DESC";
    }

    private void appendSessionFilter(ArtifactFilter filter, StringBuilder where, MapSqlParameterSource params) {

        if (filter.session() == null) {
            return;
        }
        if (filter.session() == 0L) {
            where.append(" AND a.provenance_session_id IS NULL AND NOT EXISTS (SELECT 1 FROM artifact_attachment att"
                    + " WHERE att.organization_id = a.organization_id AND att.artifact_id = a.id)");
        } else {
            where.append(" AND (a.provenance_session_id = :session OR EXISTS (SELECT 1 FROM artifact_attachment att"
                    + " WHERE att.organization_id = a.organization_id AND att.artifact_id = a.id AND att.session_id = :session))");
            params.addValue("session", filter.session());
        }
    }

    private static final String SESSIONS_SQL = """
            SELECT s.session_id AS session_id, COUNT(DISTINCT s.artifact_id) AS cnt
              FROM (
                    SELECT a.provenance_session_id AS session_id, a.id AS artifact_id
                      FROM artifact a
                     WHERE a.organization_id = :organizationId AND a.community_id = :communityId
                       AND a.status = 'ACTIVE' AND a.provenance_session_id IS NOT NULL
                    UNION
                    SELECT att.session_id AS session_id, att.artifact_id AS artifact_id
                      FROM artifact_attachment att
                      JOIN artifact a ON a.id = att.artifact_id
                     WHERE att.organization_id = :organizationId AND a.community_id = :communityId AND a.status = 'ACTIVE'
                   ) s
             GROUP BY s.session_id
             ORDER BY s.session_id
            """;

    @Override
    public List<ArtifactSession> sessionsWithArtifacts(String organizationId, Long communityId) {

        var params = new MapSqlParameterSource().addValue("organizationId", organizationId).addValue("communityId",
                communityId);
        return jdbc.query(SESSIONS_SQL, params, (rs, n) -> ArtifactSession.builder().sessionId(rs.getLong("session_id"))
                .count(rs.getLong("cnt")).build());
    }

    private static final String STATS_SQL = """
            SELECT
              (SELECT count(*) FROM artifact a
                 WHERE a.organization_id = :organizationId AND a.community_id = :communityId AND a.status = 'ACTIVE') AS total,
              (SELECT count(DISTINCT s.session_id) FROM (
                    SELECT a.provenance_session_id AS session_id
                      FROM artifact a
                     WHERE a.organization_id = :organizationId AND a.community_id = :communityId
                       AND a.status = 'ACTIVE' AND a.provenance_session_id IS NOT NULL
                    UNION
                    SELECT att.session_id AS session_id
                      FROM artifact_attachment att
                      JOIN artifact a ON a.id = att.artifact_id
                     WHERE att.organization_id = :organizationId AND a.community_id = :communityId AND a.status = 'ACTIVE'
                   ) s) AS session_count,
              (SELECT count(*) FROM artifact a
                 WHERE a.organization_id = :organizationId AND a.community_id = :communityId AND a.status = 'ACTIVE'
                   AND a.created_at >= date_trunc('month', now())) AS captured_this_month
            """;

    @Override
    public LibraryStats stats(String organizationId, Long communityId) {

        var params = new MapSqlParameterSource().addValue("organizationId", organizationId).addValue("communityId",
                communityId);
        return jdbc.queryForObject(STATS_SQL, params,
                (rs, n) -> LibraryStats.builder().total(rs.getLong("total")).sessionCount(rs.getLong("session_count"))
                        .capturedThisMonth(rs.getLong("captured_this_month")).build());
    }
}
