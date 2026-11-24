package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.ActivityTitles;
import dev.axonia.events.api.NamedSession;
import dev.axonia.events.api.SessionHeading;
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
 * {@link #topicTitles}, {@link #sessionNames} and {@link #sessionHeadings} filter on {@code organization_id} as well as
 * RLS does, the way the read repositories do. {@link #slotTitles} cannot: {@code event_slot} carries no organization
 * column and is protected only by the joins its parents are read through, so it relies on callers passing ids they are
 * already authorized to see, like every {@code event_slot} read by id alone.
 */
@Service
@RequiredArgsConstructor
public class ActivityTitleReader implements ActivityTitles {

    private static final String SESSION_NAMES_SQL = """
            SELECT s.id, s.community_id, s.title, series.title AS series_title, s.occurrence_date, s.scheduled_at,
                   s.time_zone,
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

        return sessionRows(ids).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                row -> new NamedSession(row.getValue().communityId(), row.getValue().heading().name())));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, SessionHeading> sessionHeadings(Collection<Long> ids) {

        return sessionRows(ids).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, row -> row.getValue().heading()));
    }

    private Map<Long, SessionRow> sessionRows(Collection<Long> ids) {

        if (ids.isEmpty()) {
            return Map.of();
        }
        var rows = new HashMap<Long, SessionRow>();
        var params = new MapSqlParameterSource("ids", ids).addValue("org", organizationContext.organizationId());
        jdbc.query(SESSION_NAMES_SQL, params, rs -> {
            var slotTitles = Arrays.asList((String[]) rs.getArray("slot_titles").getArray());
            var seriesTitle = rs.getString("series_title");
            var heading = SessionHeading.builder().name(SessionName.of(rs.getString("title"), slotTitles, seriesTitle))
                    .seriesTitle(seriesTitle).heldOn(heldOn(rs.getObject("occurrence_date", LocalDate.class),
                            rs.getTimestamp("scheduled_at").toInstant(), rs.getString("time_zone")))
                    .build();
            rows.put(rs.getLong("id"), new SessionRow(rs.getLong("community_id"), heading));
        });
        return rows;
    }

    /**
     * An occurrence is held on its anchor date. A one-off has none and is held on the day its instant falls on in its
     * own zone, which only a one-off created before the column existed lacks; it reads UTC, as it always has.
     */
    private static LocalDate heldOn(LocalDate occurrenceDate, Instant scheduledAt, String timeZone) {

        if (occurrenceDate != null) {
            return occurrenceDate;
        }
        var zone = timeZone == null || timeZone.isBlank() ? ZoneOffset.UTC : ZoneId.of(timeZone);
        return LocalDate.ofInstant(scheduledAt, zone);
    }

    private record SessionRow(long communityId, SessionHeading heading) {
    }

    private Map<Long, String> titles(String sql, MapSqlParameterSource params) {

        var titles = new HashMap<Long, String>();
        jdbc.query(sql, params, rs -> {
            titles.put(rs.getLong("id"), rs.getString("title"));
        });
        return titles;
    }
}
