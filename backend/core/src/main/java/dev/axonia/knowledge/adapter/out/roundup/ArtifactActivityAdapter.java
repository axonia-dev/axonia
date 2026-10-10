package dev.axonia.knowledge.adapter.out.roundup;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.roundup.api.spi.ArtifactActivity;
import lombok.RequiredArgsConstructor;

/**
 * Knowledge's implementation of the roundup's artifact-capture port. Capture is measured by attachment to a session,
 * never by an artifact's own {@code created_at}. The organization arrives as a parameter rather than through this
 * module's request-scoped {@code OrganizationContext}, because a roundup is assembled with no JWT in scope.
 */
@Component
@RequiredArgsConstructor
class ArtifactActivityAdapter implements ArtifactActivity {

    /**
     * {@code a.status = 'ACTIVE'} is the same predicate {@code SessionResourceRepositoryImpl} applies, so the letter
     * and the session page answer the same question - see the port's javadoc for why an archived artifact stopped
     * counting.
     *
     * <p>
     * {@code DISTINCT} because {@code uq_attachment} is {@code UNIQUE NULLS NOT DISTINCT (organization_id, artifact_id,
     * session_id, segment_id)}: one deck attached to two segments of a session, or at session level AND at talk level,
     * is two rows carrying the same session, title and url. A row is projected without its segment, so those two rows
     * are one resource the letter may print once. {@code a.community_id} does not change that: it is constant per
     * artifact, the same way title and url already are, so selecting it adds no new distinctness.
     *
     * <p>
     * There is deliberately no {@code AND a.community_id = ...} predicate here, unlike
     * {@code SessionResourceRepositoryImpl}: a single call answers for MANY sessions across MANY communities at once
     * (one quarterly letter, not one session), so there is no single community id to bind into the WHERE clause. The
     * per-session community check happens in Java, in {@link #capturedForSessions}, against the caller-supplied
     * {@code sessionCommunityIds} map - see that method and the port's javadoc for why.
     */
    private static final String CAPTURED_FOR_SESSIONS = """
            SELECT DISTINCT aa.session_id, a.community_id, a.title, a.url, at.label AS type_label
            FROM artifact_attachment aa
            JOIN artifact a ON a.id = aa.artifact_id
            LEFT JOIN artifact_type at ON at.id = a.type_id
            WHERE aa.organization_id = :organizationId AND aa.session_id IN (:sessionIds) AND a.status = 'ACTIVE'
            ORDER BY aa.session_id, a.title
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Runs the single bulk query above - unfiltered by community at the SQL level, since one call spans many sessions
     * across many communities - then drops any row whose artifact's own {@code communityId} does not match the
     * community {@code sessionCommunityIds} says that row's session actually belongs to. A row can only fail this check
     * by being a cross-community link: the write side no longer creates one, so this is a closed, static set of rows
     * from before that guard, not an ongoing risk.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CapturedArtifact> capturedForSessions(String organizationId, Map<Long, Long> sessionCommunityIds) {

        if (sessionCommunityIds.isEmpty()) {
            return List.of();
        }
        var params = new MapSqlParameterSource().addValue("organizationId", organizationId).addValue("sessionIds",
                sessionCommunityIds.keySet());
        var rows = jdbc.query(CAPTURED_FOR_SESSIONS, params, (rs, rowNum) -> new CapturedRow(rs.getLong("session_id"),
                rs.getLong("community_id"), rs.getString("title"), rs.getString("url"), rs.getString("type_label")));
        return rows.stream().filter(row -> Objects.equals(sessionCommunityIds.get(row.sessionId()), row.communityId()))
                .map(row -> new CapturedArtifact(row.sessionId(), row.title(), row.url(), row.typeLabel())).toList();
    }

    /** The raw row, carrying the artifact's own community id so {@link #capturedForSessions} can check it in Java. */
    private record CapturedRow(long sessionId, long communityId, String title, String url, String typeLabel) {
    }
}
