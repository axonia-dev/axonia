package dev.axonia.recognition.adapter.out.persistence;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.recognition.application.port.out.NewLoveQuery;
import dev.axonia.recognition.application.port.out.NewLoveTallies;
import dev.axonia.recognition.domain.NewLoveTally;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcNewLoveTallies implements NewLoveTallies {

    private static final String SQL = """
            SELECT rc.contribution_id, COUNT(*) AS new_loves,
                   BOOL_OR(iv.seen_at IS NULL OR iv.seen_at < :floor) AS capped
            FROM recognized_contribution rc
            JOIN contribution_love cl
              ON cl.recognized_contribution_id = rc.id AND cl.organization_id = rc.organization_id
            LEFT JOIN impact_visit iv
              ON iv.organization_id = rc.organization_id AND iv.user_id = :userId AND iv.community_id = rc.community_id
            WHERE rc.organization_id = :organizationId
              AND rc.contribution_type = :contributionType
              AND rc.contribution_id IN (:contributionIds)
              AND cl.lover_user_id <> :userId
              AND date_trunc('milliseconds', cl.created_at) > GREATEST(COALESCE(iv.seen_at, :floor), :floor)
              AND date_trunc('milliseconds', cl.created_at) <= :asOf
            GROUP BY rc.contribution_id, rc.community_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public List<NewLoveTally> since(NewLoveQuery query) {

        if (query.contributionIds().isEmpty()) {
            return List.of();
        }
        var params = new MapSqlParameterSource().addValue("organizationId", query.organizationId())
                .addValue("userId", query.userId()).addValue("contributionType", query.contributionType().name())
                .addValue("contributionIds", query.contributionIds()).addValue("floor", Timestamp.from(query.floor()))
                .addValue("asOf", Timestamp.from(query.asOf()));
        return jdbc.query(SQL, params,
                (rs, _) -> NewLoveTally.builder().contributionType(query.contributionType())
                        .contributionId(rs.getLong("contribution_id")).newLoves(rs.getLong("new_loves"))
                        .capped(rs.getBoolean("capped")).build());
    }
}
