package dev.axonia.knowledge.adapter.out.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.knowledge.api.ArtifactTitles;
import dev.axonia.knowledge.application.port.out.OrganizationContext;
import lombok.RequiredArgsConstructor;

/**
 * Read-only lookup of artifact titles by id, for the modules that reference artifacts as an activity source (e.g. the
 * expertise evidence view). One {@code IN} query, org-filtered as well as RLS is, and an id that no longer resolves is
 * simply absent from the result - see {@code ActivityTitleReader} in the events module for why absence is a value here
 * and not an exception.
 */
@Service
@RequiredArgsConstructor
public class ArtifactTitleReader implements ArtifactTitles {

    private static final String SQL = "SELECT id, title FROM artifact WHERE id IN (:ids) AND organization_id = :org";

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationContext organizationContext;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> titlesByIds(Collection<Long> ids) {

        if (ids.isEmpty()) {
            return Map.of();
        }
        var params = new MapSqlParameterSource("ids", ids).addValue("org", organizationContext.organizationId());
        var titles = new HashMap<Long, String>();
        jdbc.query(SQL, params, rs -> {
            titles.put(rs.getLong("id"), rs.getString("title"));
        });
        return titles;
    }
}
