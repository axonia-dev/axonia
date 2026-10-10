package dev.axonia.knowledge.adapter.out.recognition;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.recognition.api.ArtifactBadge;
import dev.axonia.recognition.api.spi.ArtifactTypeAccess;
import lombok.RequiredArgsConstructor;

/**
 * Resolves a captured artifact's type badge for the recognition impact profile, in a single batched read. Artifact ids
 * are globally unique, so no organization scoping is applied; callers pass ids they are already authorized to see.
 */
@Component
@RequiredArgsConstructor
class ArtifactTypeAccessAdapter implements ArtifactTypeAccess {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ArtifactBadge> artifactTypes(Collection<Long> artifactIds) {

        if (artifactIds.isEmpty()) {
            return Map.of();
        }
        var params = new MapSqlParameterSource("ids", artifactIds);
        var badges = new HashMap<Long, ArtifactBadge>();
        jdbc.query("""
                SELECT a.id AS artifact_id, t.id AS type_id, t.label AS label, t.icon AS icon,
                       t.accent_color AS accent_color
                FROM artifact a
                JOIN artifact_type t ON t.id = a.type_id
                WHERE a.id IN (:ids)
                """, params, rs -> {
            badges.put(rs.getLong("artifact_id"), new ArtifactBadge(rs.getLong("type_id"), rs.getString("label"),
                    rs.getString("icon"), rs.getString("accent_color")));
        });
        return badges;
    }
}
