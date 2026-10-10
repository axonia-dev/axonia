package dev.axonia.knowledge.adapter.out.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.knowledge.application.port.out.ArtifactLoveCounts;
import lombok.RequiredArgsConstructor;

/**
 * Writes {@code artifact.love_count}, an organization-scoped, RLS-protected column: each UPDATE relies on
 * {@code app.current_org} already being set on the connection - see {@code ArtifactLoveCountListener} for when that
 * holds.
 */
@Component
@RequiredArgsConstructor
public class JdbcArtifactLoveCounts implements ArtifactLoveCounts {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void increment(Long artifactId, Long communityId) {

        jdbc.update("UPDATE artifact SET love_count = love_count + 1 WHERE id = :id AND community_id = :communityId",
                params(artifactId, communityId));
    }

    @Override
    public void decrement(Long artifactId, Long communityId) {

        jdbc.update("UPDATE artifact SET love_count = GREATEST(love_count - 1, 0)"
                + " WHERE id = :id AND community_id = :communityId", params(artifactId, communityId));
    }

    private MapSqlParameterSource params(Long contributionId, Long communityId) {

        return new MapSqlParameterSource().addValue("id", contributionId).addValue("communityId", communityId);
    }
}
