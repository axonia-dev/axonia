package dev.axonia.recognition.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.recognition.application.port.out.ImpactVisits;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class JdbcImpactVisits implements ImpactVisits {

    private static final String UPSERT = """
            INSERT INTO impact_visit (organization_id, user_id, community_id, seen_at)
            VALUES (:organizationId, :userId, :communityId, :asOf)
            ON CONFLICT (organization_id, user_id, community_id)
            DO UPDATE SET seen_at = GREATEST(impact_visit.seen_at, EXCLUDED.seen_at)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional
    public void markSeen(String organizationId, String userId, Collection<Long> communityIds, Instant asOf) {

        var seenAt = Timestamp.from(asOf);
        var params = communityIds.stream()
                .map(communityId -> new MapSqlParameterSource().addValue("organizationId", organizationId)
                        .addValue("userId", userId).addValue("communityId", communityId).addValue("asOf", seenAt))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(UPSERT, params);
    }
}
