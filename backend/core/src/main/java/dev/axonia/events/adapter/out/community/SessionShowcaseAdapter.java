package dev.axonia.events.adapter.out.community;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.LobbySession;
import dev.axonia.community.api.spi.SessionShowcase;
import dev.axonia.events.adapter.out.persistence.SessionNameSql;
import dev.axonia.events.application.port.out.OrganizationContext;
import lombok.RequiredArgsConstructor;

/**
 * Events' answer to the communities lobby. Read-only by construction. A session that is neither cancelled nor skipped
 * is next until it is over: a timed one once its duration has run (at its start when it has none), an untimed one at
 * the end of its date in its own zone, or its series' zone. A held session is latest once it is over.
 */
@Component
@RequiredArgsConstructor
class SessionShowcaseAdapter implements SessionShowcase {

    private static final String SCOPE = "cs.organization_id = :organizationId AND cs.community_id IN (:communityIds)";

    /** The session's governing clock as {@code SessionFacts} resolves it: its own zone, its series', then UTC. */
    private static final String ZONE = """
            COALESCE(NULLIF(btrim(cs.time_zone), ''),
                     (SELECT NULLIF(btrim(zone_series.time_zone), '') FROM event_series zone_series
                      WHERE zone_series.id = cs.series_id),
                     'UTC')""";

    private static final String ENDS_AT = """
            CASE WHEN cs.has_time
                 THEN cs.scheduled_at + make_interval(mins => coalesce(cs.duration_minutes, 0))
                 ELSE ((cs.scheduled_at AT TIME ZONE {zone})::date + 1)::timestamp AT TIME ZONE {zone}
            END""".replace("{zone}", ZONE);

    /**
     * How long before {@code now} a session may have started and still be under way, so the search for one stays on the
     * {@code scheduled_at} index rather than reading the organization's whole history. An untimed day ends at most
     * about 26 hours after its stored midnight; a timed session lasting longer than this is no longer shown as under
     * way.
     */
    static final Duration UNDER_WAY_HORIZON = Duration.ofDays(7);

    private static final String NOT_OVER = "cs.status NOT IN ('CANCELLED', 'SKIPPED') AND cs.scheduled_at >= :underWayFrom"
            + " AND (cs.scheduled_at >= :now OR " + ENDS_AT + " > :now)";

    private static final String NEXT_SESSIONS_AT = """
            SELECT cs.community_id, min(cs.scheduled_at) AS scheduled_at FROM community_session cs
            WHERE {scope} AND {notOver}
            GROUP BY cs.community_id
            """.replace("{scope}", SCOPE).replace("{notOver}", NOT_OVER);

    private static final String NAMED_SESSION = """
            SELECT DISTINCT ON (cs.community_id) cs.community_id, cs.scheduled_at, cs.has_time,
            """ + ZONE + " AS zone,\n" + SessionNameSql.NAME + " AS title\n" + """
            FROM community_session cs
            """ + SessionNameSql.SOURCES + """
            WHERE {scope} AND {when}
            ORDER BY cs.community_id, {order}
            """.replace("{scope}", SCOPE);

    private static final String NEXT_SESSIONS = NAMED_SESSION.replace("{when}", NOT_OVER).replace("{order}",
            "cs.scheduled_at, cs.id");

    private static final String LATEST_SESSIONS = NAMED_SESSION
            .replace("{when}", "cs.status = 'HELD' AND cs.scheduled_at < :now AND " + ENDS_AT + " <= :now")
            .replace("{order}", "cs.scheduled_at DESC, cs.id DESC");

    private static final String HELD_COUNTS = """
            SELECT cs.community_id, count(*) AS held FROM community_session cs
            WHERE {scope} AND cs.status = 'HELD'
            GROUP BY cs.community_id
            """.replace("{scope}", SCOPE);

    private static final String LATEST_TOPICS = """
            SELECT DISTINCT ON (community_id) community_id, title FROM topic
            WHERE organization_id = :organizationId AND community_id IN (:communityIds) AND status = 'PROPOSED'
            ORDER BY community_id, created_at DESC, id DESC
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationContext organizationContext;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Instant> nextSessionsAt(Set<Long> communityIds, Instant now) {

        var next = new HashMap<Long, Instant>();
        if (communityIds.isEmpty()) {
            return next;
        }
        jdbc.query(NEXT_SESSIONS_AT, parameters(communityIds, now), rs -> {
            next.put(rs.getLong("community_id"), rs.getTimestamp("scheduled_at").toInstant());
        });
        return next;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, SessionSignals> signals(Set<Long> communityIds, Instant now) {

        if (communityIds.isEmpty()) {
            return Map.of();
        }
        var parameters = parameters(communityIds, now);
        var next = sessions(NEXT_SESSIONS, parameters);
        var latest = sessions(LATEST_SESSIONS, parameters);
        var held = new HashMap<Long, Long>();
        jdbc.query(HELD_COUNTS, parameters, rs -> {
            held.put(rs.getLong("community_id"), rs.getLong("held"));
        });
        var topics = new HashMap<Long, String>();
        jdbc.query(LATEST_TOPICS, parameters, rs -> {
            topics.put(rs.getLong("community_id"), rs.getString("title"));
        });
        var answered = new HashSet<Long>(next.keySet());
        answered.addAll(latest.keySet());
        answered.addAll(held.keySet());
        answered.addAll(topics.keySet());
        var signals = new HashMap<Long, SessionSignals>();
        answered.forEach(id -> signals.put(id,
                new SessionSignals(next.get(id), latest.get(id), held.getOrDefault(id, 0L), topics.get(id))));
        return signals;
    }

    private Map<Long, LobbySession> sessions(String sql, MapSqlParameterSource parameters) {

        var sessions = new HashMap<Long, LobbySession>();
        jdbc.query(sql, parameters, rs -> {
            sessions.put(rs.getLong("community_id"), new LobbySession(rs.getString("title"),
                    rs.getTimestamp("scheduled_at").toInstant(), rs.getBoolean("has_time"), rs.getString("zone")));
        });
        return sessions;
    }

    private MapSqlParameterSource parameters(Set<Long> communityIds, Instant now) {

        return new MapSqlParameterSource().addValue("organizationId", organizationContext.organizationId())
                .addValue("communityIds", communityIds).addValue("now", Timestamp.from(now))
                .addValue("underWayFrom", Timestamp.from(now.minus(UNDER_WAY_HORIZON)));
    }
}
