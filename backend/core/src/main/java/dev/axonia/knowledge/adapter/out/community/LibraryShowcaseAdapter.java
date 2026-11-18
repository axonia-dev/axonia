package dev.axonia.knowledge.adapter.out.community;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.spi.LibraryShowcase;
import dev.axonia.knowledge.application.port.out.OrganizationContext;
import lombok.RequiredArgsConstructor;

/**
 * Knowledge's answer to the communities lobby: the artifacts a community shares, by the library's own visibility rule
 * (an archived artifact is withdrawn from it), and the newest one's title.
 */
@Component
@RequiredArgsConstructor
class LibraryShowcaseAdapter implements LibraryShowcase {

    private static final String SHARED = """
            SELECT DISTINCT ON (community_id) community_id,
                   count(*) OVER (PARTITION BY community_id) AS shared, title
            FROM artifact
            WHERE organization_id = :organizationId AND community_id IN (:communityIds) AND status = 'ACTIVE'
            ORDER BY community_id, created_at DESC, id DESC
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationContext organizationContext;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, LibrarySignals> signals(Set<Long> communityIds) {

        var signals = new HashMap<Long, LibrarySignals>();
        if (communityIds.isEmpty()) {
            return signals;
        }
        var parameters = new MapSqlParameterSource().addValue("organizationId", organizationContext.organizationId())
                .addValue("communityIds", communityIds);
        jdbc.query(SHARED, parameters, rs -> {
            signals.put(rs.getLong("community_id"), new LibrarySignals(rs.getLong("shared"), rs.getString("title")));
        });
        return signals;
    }
}
