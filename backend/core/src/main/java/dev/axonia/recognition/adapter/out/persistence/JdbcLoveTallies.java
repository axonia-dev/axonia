package dev.axonia.recognition.adapter.out.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.application.port.out.LoveTallies;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class JdbcLoveTallies implements LoveTallies {

    private static final String SQL = """
            SELECT rc.contribution_id, COUNT(*) AS loves
            FROM recognized_contribution rc
            JOIN contribution_love cl
              ON cl.recognized_contribution_id = rc.id AND cl.organization_id = rc.organization_id
            WHERE rc.organization_id = :organizationId
              AND rc.contribution_type = :contributionType
              AND rc.contribution_id IN (:contributionIds)
            GROUP BY rc.contribution_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Map<Long, Long> countFor(String organizationId, ContributionType contributionType,
            Collection<Long> contributionIds) {

        var params = new MapSqlParameterSource().addValue("organizationId", organizationId)
                .addValue("contributionType", contributionType.name()).addValue("contributionIds", contributionIds);
        var counts = new HashMap<Long, Long>();
        jdbc.query(SQL, params, rs -> {
            counts.put(rs.getLong("contribution_id"), rs.getLong("loves"));
        });
        return counts;
    }
}
