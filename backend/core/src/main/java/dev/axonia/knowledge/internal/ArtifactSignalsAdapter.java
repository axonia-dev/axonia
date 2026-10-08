package dev.axonia.knowledge.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.health.ArtifactSignals;
import dev.axonia.knowledge.OrganizationContext;
import lombok.RequiredArgsConstructor;

/**
 * Knowledge's implementation of the artifact-capture health signal. Counts capture acts regardless of later archival.
 */
@Component
@RequiredArgsConstructor
class ArtifactSignalsAdapter implements ArtifactSignals {

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationContext organizationContext;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> artifactCounts(long communityId, Instant from, Instant to) {

        var sql = """
                SELECT contributor_user_id, count(*) AS acts FROM artifact
                WHERE organization_id = :organizationId AND community_id = :communityId
                  AND created_at >= :from AND created_at < :to
                GROUP BY contributor_user_id
                """;
        var params = new MapSqlParameterSource().addValue("organizationId", organizationContext.organizationId())
                .addValue("communityId", communityId).addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));
        var counts = new HashMap<String, Long>();
        jdbc.query(sql, params, rs -> {
            counts.put(rs.getString("contributor_user_id"), rs.getLong("acts"));
        });
        return counts;
    }
}
