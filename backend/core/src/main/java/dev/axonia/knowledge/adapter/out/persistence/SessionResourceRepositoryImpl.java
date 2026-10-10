package dev.axonia.knowledge.adapter.out.persistence;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.axonia.knowledge.application.port.out.SessionResourceRepository;
import dev.axonia.knowledge.domain.SessionResourceRow;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SessionResourceRepositoryImpl implements SessionResourceRepository {

    private static final String SQL = """
            SELECT att.id AS attachment_id, att.artifact_id, att.segment_id, att.attached_by_user_id
              FROM artifact_attachment att
              JOIN artifact a ON a.id = att.artifact_id
             WHERE att.organization_id = :organizationId AND att.session_id = :sessionId AND a.status = 'ACTIVE'
               AND a.community_id = :communityId
            UNION ALL
            SELECT NULL AS attachment_id, a.id AS artifact_id, a.provenance_segment_id AS segment_id,
                   NULL AS attached_by_user_id
              FROM artifact a
             WHERE a.organization_id = :organizationId AND a.provenance_session_id = :sessionId AND a.status = 'ACTIVE'
               AND a.community_id = :communityId
               AND NOT EXISTS (
                   SELECT 1 FROM artifact_attachment att2
                    WHERE att2.organization_id = a.organization_id AND att2.artifact_id = a.id AND att2.session_id = :sessionId)
             ORDER BY artifact_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<SessionResourceRow> findForSession(String organizationId, Long sessionId, Long communityId) {

        var params = new MapSqlParameterSource().addValue("organizationId", organizationId)
                .addValue("sessionId", sessionId).addValue("communityId", communityId);
        return jdbc.query(SQL, params, (rs, n) -> new SessionResourceRow((Long) rs.getObject("attachment_id"),
                rs.getLong("artifact_id"), (Long) rs.getObject("segment_id"), rs.getString("attached_by_user_id")));
    }
}
