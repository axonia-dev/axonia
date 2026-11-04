package dev.axonia.events.adapter.out.persistence;

/**
 * The {@code SessionName} rule stated in SQL, for queries that name sessions in bulk: the session's own title, then its
 * first titled slot, then its series' title. It stops short of the rule's generic "Session", which each query adds or
 * leaves to its renderer.
 *
 * <p>
 * Every step runs through the blank-is-absent rule {@code SessionName} applies in Java, hence the
 * {@code NULLIF(btrim(...), '')}: {@code community_session.title} is nullable but not checked against empty, and a
 * blank one returned as {@code ""} would print an empty name.
 *
 * <p>
 * {@link #NAME} reads the aliases {@link #SOURCES} joins, against a session aliased {@code cs}.
 */
public final class SessionNameSql {

    public static final String NAME = """
            COALESCE(NULLIF(btrim(cs.title), ''), name_slot.title, NULLIF(btrim(name_series.title), ''))""";

    public static final String SOURCES = """
            LEFT JOIN event_series name_series ON name_series.id = cs.series_id
            LEFT JOIN LATERAL (
                SELECT name_sl.title
                FROM event_slot name_sl
                JOIN event_section name_sec ON name_sl.section_id = name_sec.id
                WHERE name_sec.session_id = cs.id AND btrim(coalesce(name_sl.title, '')) <> ''
                ORDER BY name_sec.position, name_sl.position
                LIMIT 1
            ) name_slot ON true
            """;

    private SessionNameSql() {
    }
}
