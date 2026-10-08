package dev.axonia.events.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.UserProfileAccess;
import dev.axonia.roundup.SessionActivity;
import lombok.RequiredArgsConstructor;

/**
 * Events' implementation of the roundup's session-activity port. Read-only by construction; windows are [from, to), and
 * the organization arrives as a parameter rather than through this module's request-scoped {@code OrganizationContext},
 * because a roundup is assembled with no JWT in scope.
 *
 * <p>
 * Presenter names are resolved through {@link UserProfileAccess}, the same port every session mail already resolves
 * them with, so an erased subject's surrogate id can never reach an inbox as text.
 */
@Component
@RequiredArgsConstructor
class SessionActivityAdapter implements SessionActivity {

    /**
     * The title the app and every session mail already show: the first titled slot in the session, then the session's
     * own title. A series occurrence routinely carries no title of its own - it is identified by its series and its
     * date - so reading {@code community_session.title} alone would head most of a roundup's lines with nothing.
     *
     * <p>
     * Both arms run through the same blank-is-absent rule {@code SessionFacts.sessionTitle} applies in Java, hence the
     * {@code NULLIF(btrim(...), '')} on the session's own title as well as on the slot's: {@code
     * community_session.title} is nullable but not checked against empty, and a blank one returned as {@code ""} would
     * leave the renderer's no-title state unfired and print an empty heading.
     */
    private static final String FIRST_SLOT_TITLE = """
            LEFT JOIN LATERAL (
                SELECT sl.title
                FROM event_slot sl
                JOIN event_section sec ON sl.section_id = sec.id
                WHERE sec.session_id = cs.id AND btrim(coalesce(sl.title, '')) <> ''
                ORDER BY sec.position, sl.position
                LIMIT 1
            ) slot_title ON true
            """;

    /**
     * How many talks one session's agenda carried, across all of its sections. A correlated subquery rather than a join
     * with a {@code GROUP BY}: the outer query already {@code LEFT JOIN}s a lateral for the title, and adding a second
     * multiplying join would then have to group every selected column back down again for no gain. Counts slots
     * regardless of whether anybody presented them - a slot that ran unattributed still happened - so this number is
     * deliberately independent of {@code presenterNames}, which the roundup states beside it.
     */
    private static final String TALK_COUNT = """
            (SELECT count(*)
             FROM event_slot count_sl
             JOIN event_section count_sec ON count_sl.section_id = count_sec.id
             WHERE count_sec.session_id = cs.id) AS talk_count
            """;

    private static final String HELD_SESSIONS = """
            SELECT cs.id, cs.community_id, cs.scheduled_at,
                   COALESCE(slot_title.title, NULLIF(btrim(cs.title), '')) AS title,
            """ + TALK_COUNT + """
            FROM community_session cs
            """ + FIRST_SLOT_TITLE + """
            WHERE cs.organization_id = :organizationId
              AND cs.status NOT IN ('CANCELLED', 'SKIPPED')
              AND cs.scheduled_at >= :from AND cs.scheduled_at < :to
            ORDER BY cs.scheduled_at, cs.id
            """;

    private static final String UPCOMING_SESSIONS = """
            SELECT cs.community_id, cs.scheduled_at,
                   COALESCE(slot_title.title, NULLIF(btrim(cs.title), '')) AS title
            FROM community_session cs
            """ + FIRST_SLOT_TITLE + """
            WHERE cs.organization_id = :organizationId
              AND cs.status = 'SCHEDULED'
              AND cs.scheduled_at >= :from AND cs.scheduled_at < :to
            ORDER BY cs.scheduled_at, cs.id
            """;

    /**
     * How many slots in the window's {@code SCHEDULED} sessions have nobody presenting them. Counts slots rather than
     * sessions, and scopes to {@code SCHEDULED} occurrences only, matching {@link #UPCOMING_SESSIONS}'s window: an
     * empty slot on a cancelled session is not an invitation to fill it.
     */
    private static final String OPEN_TALKS = """
            SELECT count(*)
            FROM event_slot sl
            JOIN event_section sec ON sl.section_id = sec.id
            JOIN community_session cs ON sec.session_id = cs.id
            WHERE cs.organization_id = :organizationId
              AND cs.status = 'SCHEDULED'
              AND cs.scheduled_at >= :from AND cs.scheduled_at < :to
              AND NOT EXISTS (SELECT 1 FROM event_slot_presenter sp WHERE sp.slot_id = sl.id)
            """;

    /**
     * The presenters of the window's sessions who appear on no earlier session anywhere in the organization. The inner
     * NOT EXISTS is bounded by {@code scheduled_at < :from} and by nothing else: it looks across every community, so a
     * regular of one guild presenting in another is counted as the returning speaker they are.
     */
    private static final String FIRST_TIME_PRESENTERS = """
            SELECT count(DISTINCT sp.user_id)
            FROM event_slot_presenter sp
            JOIN event_slot sl ON sp.slot_id = sl.id
            JOIN event_section sec ON sl.section_id = sec.id
            JOIN community_session cs ON sec.session_id = cs.id
            WHERE cs.organization_id = :organizationId
              AND cs.status NOT IN ('CANCELLED', 'SKIPPED')
              AND cs.scheduled_at >= :from AND cs.scheduled_at < :to
              AND NOT EXISTS (
                  SELECT 1
                  FROM event_slot_presenter earlier_sp
                  JOIN event_slot earlier_sl ON earlier_sp.slot_id = earlier_sl.id
                  JOIN event_section earlier_sec ON earlier_sl.section_id = earlier_sec.id
                  JOIN community_session earlier_cs ON earlier_sec.session_id = earlier_cs.id
                  WHERE earlier_sp.user_id = sp.user_id
                    AND earlier_cs.organization_id = :organizationId
                    AND earlier_cs.status NOT IN ('CANCELLED', 'SKIPPED')
                    AND earlier_cs.scheduled_at < :from)
            """;

    private static final String PRESENTERS_OF_SESSIONS = """
            SELECT sec.session_id, sp.user_id
            FROM event_slot_presenter sp
            JOIN event_slot sl ON sp.slot_id = sl.id
            JOIN event_section sec ON sl.section_id = sec.id
            WHERE sec.session_id IN (:sessionIds)
            ORDER BY sec.session_id, sec.position, sl.position
            """;

    /**
     * Every titled slot of the given sessions, in agenda order. Blank titles are dropped here rather than in Java, by
     * the same {@code btrim(coalesce(sl.title, '')) <> ''} predicate {@link #FIRST_SLOT_TITLE} already picks its one
     * slot with, so a slot titled with a space cannot reach a template as an empty line.
     */
    private static final String TALK_TITLES_OF_SESSIONS = """
            SELECT sec.session_id, sl.title
            FROM event_slot sl
            JOIN event_section sec ON sl.section_id = sec.id
            WHERE sec.session_id IN (:sessionIds)
              AND btrim(coalesce(sl.title, '')) <> ''
            ORDER BY sec.session_id, sec.position, sl.position
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final UserProfileAccess userProfileAccess;

    @Override
    @Transactional(readOnly = true)
    public List<HeldSession> heldInPeriod(String organizationId, Instant from, Instant to) {

        var sessions = jdbc.query(HELD_SESSIONS, window(organizationId, from, to),
                (rs, rowNum) -> new HeldRow(rs.getLong("id"), rs.getLong("community_id"), rs.getString("title"),
                        rs.getTimestamp("scheduled_at").toInstant(), rs.getInt("talk_count")));
        if (sessions.isEmpty()) {
            return List.of();
        }
        var presenterIds = presenterIdsBySession(sessions.stream().map(HeldRow::id).toList());
        var talkTitles = talkTitlesBySession(sessions.stream().map(HeldRow::id).toList());
        var names = displayNames(organizationId,
                presenterIds.values().stream().flatMap(List::stream).distinct().toList());
        return sessions.stream()
                .map(row -> HeldSession.builder().sessionId(row.id()).communityId(row.communityId()).title(row.title())
                        .heldAt(row.heldAt()).talkCount(row.talkCount())
                        .talkTitles(talkTitles.getOrDefault(row.id(), List.of()))
                        .presenterNames(namesOf(presenterIds.getOrDefault(row.id(), List.of()), names)).build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UpcomingSession> scheduledAfter(String organizationId, Instant from, Instant to) {

        return jdbc.query(UPCOMING_SESSIONS, window(organizationId, from, to),
                (rs, rowNum) -> new UpcomingSession(rs.getLong("community_id"), rs.getString("title"),
                        rs.getTimestamp("scheduled_at").toInstant()));
    }

    @Override
    @Transactional(readOnly = true)
    public int openTalkCount(String organizationId, Instant from, Instant to) {

        return Objects.requireNonNull(jdbc.queryForObject(OPEN_TALKS, window(organizationId, from, to), Integer.class));
    }

    @Override
    @Transactional(readOnly = true)
    public int firstTimePresenterCount(String organizationId, Instant from, Instant to) {

        return Objects.requireNonNull(
                jdbc.queryForObject(FIRST_TIME_PRESENTERS, window(organizationId, from, to), Integer.class));
    }

    /**
     * One session's presenters in the order they appear on its agenda, each person once however many slots they hold.
     */
    private Map<Long, List<String>> presenterIdsBySession(List<Long> sessionIds) {

        var bySession = new LinkedHashMap<Long, List<String>>();
        jdbc.query(PRESENTERS_OF_SESSIONS, new MapSqlParameterSource("sessionIds", sessionIds), rs -> {
            var presenters = bySession.computeIfAbsent(rs.getLong("session_id"), sessionId -> new ArrayList<>());
            var userId = rs.getString("user_id");
            if (!presenters.contains(userId)) {
                presenters.add(userId);
            }
        });
        return bySession;
    }

    private Map<Long, List<String>> talkTitlesBySession(List<Long> sessionIds) {

        var bySession = new LinkedHashMap<Long, List<String>>();
        jdbc.query(TALK_TITLES_OF_SESSIONS, new MapSqlParameterSource("sessionIds", sessionIds), rs -> {
            bySession.computeIfAbsent(rs.getLong("session_id"), sessionId -> new ArrayList<>())
                    .add(rs.getString("title"));
        });
        return bySession;
    }

    /** A quarter whose sessions had nobody presenting asks the directory nothing rather than for an empty set. */
    private Map<String, String> displayNames(String organizationId, Collection<String> userIds) {

        return userIds.isEmpty() ? Map.of() : userProfileAccess.displayNames(organizationId, userIds);
    }

    /**
     * A presenter the directory could not name is dropped rather than rendered, exactly as {@link SessionFacts} already
     * drops one: the templates have a state for a talk with no presenter, and none for a raw id.
     */
    private static List<String> namesOf(List<String> userIds, Map<String, String> names) {

        return userIds.stream().map(names::get).filter(name -> name != null && !name.isBlank()).toList();
    }

    private static MapSqlParameterSource window(String organizationId, Instant from, Instant to) {

        return new MapSqlParameterSource().addValue("organizationId", organizationId)
                .addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to));
    }

    private record HeldRow(long id, long communityId, String title, Instant heldAt, int talkCount) {
    }
}
