package dev.axonia.events.adapter.out.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.ActivityTitles;
import dev.axonia.events.application.port.out.OrganizationContext;
import lombok.RequiredArgsConstructor;

/**
 * Read-only lookup of the titles this module owns - slots and topics - for the modules that reference them as activity
 * sources (e.g. the expertise evidence view). Each lookup is one {@code IN} query, and an id that no longer resolves is
 * simply absent from the result.
 *
 * <p>
 * Absence is a value here, never an exception. A caller resolving a title already holds a reference that outlived what
 * it points at, so a miss is an ordinary outcome; and going through an aggregate read that throws would poison the
 * caller's transaction, because a {@code @Transactional} method that throws marks the transaction it joined
 * rollback-only whether or not the caller catches.
 *
 * <p>
 * {@link #topicTitles} filters on {@code organization_id} as well as RLS does, the way the read repositories do.
 * {@link #slotTitles} cannot: {@code event_slot} carries no organization column and is protected only by the joins its
 * parents are read through, so it relies on callers passing ids they are already authorized to see, like every
 * {@code event_slot} read by id alone.
 */
@Service
@RequiredArgsConstructor
public class ActivityTitleReader implements ActivityTitles {

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationContext organizationContext;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> slotTitles(Collection<Long> ids) {

        if (ids.isEmpty()) {
            return Map.of();
        }
        return titles("SELECT id, title FROM event_slot WHERE id IN (:ids)", new MapSqlParameterSource("ids", ids));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> topicTitles(Collection<Long> ids) {

        if (ids.isEmpty()) {
            return Map.of();
        }
        var params = new MapSqlParameterSource("ids", ids).addValue("org", organizationContext.organizationId());
        return titles("SELECT id, title FROM topic WHERE id IN (:ids) AND organization_id = :org", params);
    }

    private Map<Long, String> titles(String sql, MapSqlParameterSource params) {

        var titles = new HashMap<Long, String>();
        jdbc.query(sql, params, rs -> {
            titles.put(rs.getLong("id"), rs.getString("title"));
        });
        return titles;
    }
}
