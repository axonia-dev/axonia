package dev.axonia.events.adapter.out.persistence;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.ActivityTitles;
import dev.axonia.events.api.NamedSession;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.domain.SessionName;
import lombok.RequiredArgsConstructor;

/**
 * Read-only lookup of the titles this module owns - sessions, slots and topics - for the modules that reference them as
 * activity sources (e.g. the expertise evidence view). Each lookup is one {@code IN} query, and an id that no longer
 * resolves is simply absent from the result.
 *
 * <p>
 * Absence is a value here, never an exception. A caller resolving a title already holds a reference that outlived what
 * it points at, so a miss is an ordinary outcome; and going through an aggregate read that throws would poison the
 * caller's transaction, because a {@code @Transactional} method that throws marks the transaction it joined
 * rollback-only whether or not the caller catches.
 *
 * <p>
 * {@link #topicTitles} and {@link #sessionNames} filter on {@code organization_id} as well as RLS does, the way the
 * read repositories do. {@link #slotTitles} cannot: {@code event_slot} carries no organization column and is protected
 * only by the joins its parents are read through, so it relies on callers passing ids they are already authorized to
 * see, like every {@code event_slot} read by id alone.
 */
@Service
@RequiredArgsConstructor
public class ActivityTitleReader implements ActivityTitles {

    private static final String SESSION_NAMES_SQL = """
            SELECT s.id, s.community_id, s.title, series.title AS series_title,
                   ARRAY(SELECT slot.title
                           FROM event_section section
                           JOIN event_slot slot ON slot.section_id = section.id
                          WHERE section.session_id = s.id AND slot.title IS NOT NULL
                          ORDER BY section."position", slot."position") AS slot_titles
              FROM community_session s
              LEFT JOIN event_series series ON series.id = s.series_id
             WHERE s.id IN (:ids) AND s.organization_id = :org
            """;

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

    @Override
    @Transactional(readOnly = true)
    public Map<Long, NamedSession> sessionNames(Collection<Long> ids) {

        if (ids.isEmpty()) {
            return Map.of();
        }
        var names = new HashMap<Long, NamedSession>();
        var params = new MapSqlParameterSource("ids", ids).addValue("org", organizationContext.organizationId());
        jdbc.query(SESSION_NAMES_SQL, params, rs -> {
            var slotTitles = Arrays.asList((String[]) rs.getArray("slot_titles").getArray());
            names.put(rs.getLong("id"), new NamedSession(rs.getLong("community_id"),
                    SessionName.of(rs.getString("title"), slotTitles, rs.getString("series_title"))));
        });
        return names;
    }

    private Map<Long, String> titles(String sql, MapSqlParameterSource params) {

        var titles = new HashMap<Long, String>();
        jdbc.query(sql, params, rs -> {
            titles.put(rs.getLong("id"), rs.getString("title"));
        });
        return titles;
    }
}
