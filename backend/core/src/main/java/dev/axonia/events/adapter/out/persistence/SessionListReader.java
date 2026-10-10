package dev.axonia.events.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.Session;
import dev.axonia.events.application.SessionMapper;
import dev.axonia.events.application.SessionPageQuery;
import dev.axonia.events.application.port.out.SessionListing;
import dev.axonia.events.domain.SectionRecord;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SlotPresenter;
import dev.axonia.events.domain.SlotRecord;
import dev.axonia.events.domain.SlotTag;
import lombok.RequiredArgsConstructor;

/**
 * Reads a page of sessions - the whole four-level tree, sections to slots to presenters and tags - in one query per
 * level rather than one query per parent row.
 *
 * <p>
 * {@link SessionEntity} is a four-level Spring Data JDBC aggregate, and Spring Data JDBC issues one query per parent
 * row per collection: a page of 20 sessions of 4 sections of 3 slots is 1 + 20 + 80 + 240 + 240 statements, measured at
 * 587 for exactly that page. Single Query Loading does not rescue it - it explicitly does not support multi-level
 * aggregates - so there is no configuration that makes {@code JdbcAggregateTemplate} read this shape in constant cost.
 * Reading each level once, keyed by the previous level's ids, is five statements whatever the page size.
 *
 * <p>
 * The approach is one query per level, not one join: a flat join would return 240 duplicated session rows for a
 * 20-session page and move the de-duplication into Java for no gain.
 *
 * <p>
 * <b>The write model is untouched.</b> This is a read path only. {@code SessionEntity} and its aggregate stay exactly
 * as they are, the single-session reads and every write still load them through the repository, and the entities
 * assembled here exist only to be handed straight to {@link SessionMapper#toSessions}: mapping entity to DTO stays in
 * one place, so a field this reader forgot to select is a field the mapper reads as null rather than a shape the mapper
 * never sees. {@code ReadPathQueryBudgetTest} compares one fully populated session read both ways, field by field.
 *
 * <p>
 * <b>Tenant scoping.</b> {@link #ROOTS_SQL} repeats {@code organization_id} and {@code community_id} even though RLS
 * enforces the first of them, matching {@code ArtifactSearchRepositoryImpl}'s convention: deliberately redundant,
 * because this suite cannot tell "correctly scoped" from "coincidentally correct" - Testcontainers connects as the
 * schema owner, who bypasses RLS by ownership - so it has to be right by construction rather than by testing. The four
 * child queries carry no tenant predicate because their tables have no {@code organization_id} column and, per
 * {@code V1__baseline.sql} and {@code V3__slot_tag.sql}, no RLS policy of their own: {@code event_section},
 * {@code event_slot}, {@code event_slot_presenter} and {@code event_slot_tag} are isolated structurally, by the
 * FK-cascaded parent id, exactly as {@code artifact_tag} is. Every id they are keyed by is the output of the scoped
 * query above them, so the chain is anchored on the tenant at its first hop and nowhere else needs to be. This is
 * considered, not missed.
 *
 * <p>
 * <b>Every {@code IN} is guarded.</b> An empty {@code IN ()} is a syntax error in Postgres, and hand-written SQL does
 * not short-circuit the way a derived Spring Data query does. A page with no rows, a page of one-off sessions with no
 * sections, a section with no slots: each is a real case, and each returns early rather than composing the empty list
 * into a statement.
 */
@Component
@RequiredArgsConstructor
public class SessionListReader implements SessionListing {

    private final NamedParameterJdbcTemplate jdbc;
    private final SessionMapper sessionMapper;

    /**
     * One page of sessions, mapped exactly as every other projection in this module maps them - through
     * {@link SessionMapper#toSessions}, which resolves the standing place, the section type and the attendee count once
     * for the whole page.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Session> page(SessionPageQuery query) {

        var roots = roots(query);
        if (roots.isEmpty()) {
            return List.of();
        }
        return sessionMapper.toSessions(withSections(roots));
    }

    /**
     * The page itself. {@code id} is a tiebreaker on the sort, not decoration: {@code scheduled_at} is not unique - two
     * sessions of one community can be scheduled to the same instant - and an {@code ORDER BY} that leaves ties
     * unordered lets Postgres answer them in a different order for each {@code LIMIT} / {@code OFFSET}, which repeats
     * one session on two consecutive pages and drops another entirely. It runs the same way as the sort it breaks, so
     * the whole order stays consistent: descending for the retrospective page, ascending for the other two.
     *
     * <p>
     * It does not change which sessions match, so the counts beside the paged windows - {@code countPast} and
     * {@code countUpcoming}, still criteria-built on the repository - stay consistent with it.
     */
    private List<SessionRecord> roots(SessionPageQuery query) {

        var params = new MapSqlParameterSource().addValue("organizationId", query.organizationId())
                .addValue("communityId", query.communityId());
        var sql = new StringBuilder(ROOTS_SQL);
        appendWindow(query, sql, params);
        sql.append(query.window() == SessionPageQuery.Window.PAST
                ? " ORDER BY scheduled_at DESC, id DESC"
                : " ORDER BY scheduled_at ASC, id ASC");
        appendPage(query.pageable(), sql, params);
        return jdbc.query(sql.toString(), params, SessionListReader::mapSession);
    }

    /**
     * The window predicate, said once for all three windows. {@code BETWEEN} keeps cancelled sessions - the schedule
     * shows a called-off occurrence rather than a hole - while the two paged windows drop them, and {@code from} /
     * {@code to} narrow whichever window is asked for, {@code from} inclusive and {@code to} exclusive.
     */
    private void appendWindow(SessionPageQuery query, StringBuilder sql, MapSqlParameterSource params) {

        if (query.window() != SessionPageQuery.Window.BETWEEN) {
            sql.append(" AND status <> 'CANCELLED'");
            sql.append(query.window() == SessionPageQuery.Window.PAST
                    ? " AND scheduled_at < :now"
                    : " AND scheduled_at >= :now");
            params.addValue("now", Timestamp.from(query.now()));
        }
        if (query.status() != null) {
            sql.append(" AND status = :status");
            params.addValue("status", query.status().name());
        }
        if (query.from() != null) {
            sql.append(" AND scheduled_at >= :from");
            params.addValue("from", Timestamp.from(query.from()));
        }
        if (query.to() != null) {
            sql.append(" AND scheduled_at < :to");
            params.addValue("to", Timestamp.from(query.to()));
        }
    }

    /** The schedule's window is read whole, so an absent or unpaged {@code Pageable} means no {@code LIMIT} at all. */
    private void appendPage(Pageable pageable, StringBuilder sql, MapSqlParameterSource params) {

        if (pageable == null || pageable.isUnpaged()) {
            return;
        }
        sql.append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
    }

    private List<SessionRecord> withSections(List<SessionRecord> roots) {

        var sections = sectionsBySessionId(roots.stream().map(SessionRecord::getId).toList());
        return roots.stream().map(s -> s.toBuilder().sections(sections.getOrDefault(s.getId(), List.of())).build())
                .toList();
    }

    /**
     * Every section of every session on the page, in {@code position} order - the order the aggregate's
     * {@code @MappedCollection(keyColumn = "position")} reads them in, restated here because a hand-written query has
     * no such declaration to inherit.
     */
    private Map<Long, List<SectionRecord>> sectionsBySessionId(List<Long> sessionIds) {

        var rows = jdbc.query(SECTIONS_SQL, new MapSqlParameterSource("ids", sessionIds),
                SessionListReader::mapSection);
        if (rows.isEmpty()) {
            return Map.of();
        }
        var slots = slotsBySectionId(rows.stream().map(SectionRow::id).toList());
        var bySessionId = new LinkedHashMap<Long, List<SectionRecord>>();
        for (var row : rows) {
            bySessionId.computeIfAbsent(row.sessionId(), id -> new ArrayList<>())
                    .add(row.toRecord(slots.getOrDefault(row.id(), List.of())));
        }
        return bySessionId;
    }

    /** Every slot of every section on the page, in {@code position} order, with its presenters and its own tags. */
    private Map<Long, List<SlotRecord>> slotsBySectionId(List<Long> sectionIds) {

        var rows = jdbc.query(SLOTS_SQL, new MapSqlParameterSource("ids", sectionIds), SessionListReader::mapSlot);
        if (rows.isEmpty()) {
            return Map.of();
        }
        var slotIds = rows.stream().map(SlotRow::id).toList();
        var presenters = presentersBySlotId(slotIds);
        var tags = tagsBySlotId(slotIds);
        var bySectionId = new LinkedHashMap<Long, List<SlotRecord>>();
        for (var row : rows) {
            bySectionId.computeIfAbsent(row.sectionId(), id -> new ArrayList<>()).add(
                    row.toRecord(presenters.getOrDefault(row.id(), Set.of()), tags.getOrDefault(row.id(), Set.of())));
        }
        return bySectionId;
    }

    private Map<Long, Set<SlotPresenter>> presentersBySlotId(List<Long> slotIds) {

        var bySlotId = new HashMap<Long, Set<SlotPresenter>>();
        jdbc.query(PRESENTERS_SQL, new MapSqlParameterSource("ids", slotIds), rs -> {
            bySlotId.computeIfAbsent(rs.getLong("slot_id"), id -> new LinkedHashSet<>())
                    .add(SlotPresenter.builder().userId(rs.getString("user_id")).build());
        });
        return bySlotId;
    }

    private Map<Long, Set<SlotTag>> tagsBySlotId(List<Long> slotIds) {

        var bySlotId = new HashMap<Long, Set<SlotTag>>();
        jdbc.query(TAGS_SQL, new MapSqlParameterSource("ids", slotIds), rs -> {
            bySlotId.computeIfAbsent(rs.getLong("slot_id"), id -> new LinkedHashSet<>())
                    .add(SlotTag.builder().tag(rs.getString("tag")).build());
        });
        return bySlotId;
    }

    private static SessionRecord mapSession(ResultSet rs, int rowNum) throws SQLException {

        return SessionRecord.builder().id(rs.getLong("id")).organizationId(rs.getString("organization_id"))
                .seriesId(rs.getObject("series_id", Long.class)).communityId(rs.getLong("community_id"))
                .status(rs.getString("status")).scheduledAt(instant(rs, "scheduled_at"))
                .hasTime(rs.getBoolean("has_time")).occurrenceDate(rs.getObject("occurrence_date", LocalDate.class))
                .title(rs.getString("title")).durationMinutes(rs.getObject("duration_minutes", Integer.class))
                .facilitatorUserId(rs.getString("facilitator_user_id")).notes(rs.getString("notes"))
                .recordingUrl(rs.getString("recording_url")).location(rs.getString("location"))
                .joinUrl(rs.getString("join_url")).joiningDetails(rs.getString("joining_details"))
                .timeZone(rs.getString("time_zone")).closedAt(instant(rs, "closed_at"))
                .wrapUpReleaseAt(instant(rs, "wrap_up_release_at"))
                .upcomingReleaseAt(instant(rs, "upcoming_release_at")).createdAt(instant(rs, "created_at"))
                .updatedAt(instant(rs, "updated_at")).version(rs.getLong("version")).build();
    }

    private static SectionRow mapSection(ResultSet rs, int rowNum) throws SQLException {

        return new SectionRow(rs.getLong("id"), rs.getLong("session_id"), rs.getObject("section_type_id", Long.class),
                rs.getString("title"), rs.getObject("duration_minutes", Integer.class), rs.getString("notes"));
    }

    private static SlotRow mapSlot(ResultSet rs, int rowNum) throws SQLException {

        return new SlotRow(rs.getLong("id"), rs.getLong("section_id"), rs.getObject("topic_id", Long.class),
                rs.getString("title"), rs.getString("notes"), rs.getObject("duration_minutes", Integer.class));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {

        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    /** One {@code event_section} row, carrying the parent id the entity itself does not model. */
    private record SectionRow(Long id, Long sessionId, Long sectionTypeId, String title, Integer durationMinutes,
            String notes) {

        SectionRecord toRecord(List<SlotRecord> slots) {

            return SectionRecord.builder().id(id).sectionTypeId(sectionTypeId).title(title)
                    .durationMinutes(durationMinutes).notes(notes).slots(slots).build();
        }
    }

    /** One {@code event_slot} row, carrying the parent id the entity itself does not model. */
    private record SlotRow(Long id, Long sectionId, Long topicId, String title, String notes, Integer durationMinutes) {

        SlotRecord toRecord(Set<SlotPresenter> presenters, Set<SlotTag> tags) {

            return SlotRecord.builder().id(id).topicId(topicId).title(title).notes(notes)
                    .durationMinutes(durationMinutes).presenters(presenters).tags(tags).build();
        }
    }

    private static final String ROOTS_SQL = """
            SELECT id, organization_id, series_id, community_id, status, scheduled_at, has_time, occurrence_date,
                   title, duration_minutes, facilitator_user_id, notes, recording_url, location, join_url,
                   joining_details, time_zone, closed_at, wrap_up_release_at, upcoming_release_at, created_at, updated_at, version
              FROM community_session
             WHERE organization_id = :organizationId AND community_id = :communityId
            """;

    private static final String SECTIONS_SQL = """
            SELECT id, session_id, section_type_id, title, duration_minutes, notes
              FROM event_section
             WHERE session_id IN (:ids)
             ORDER BY session_id, "position"
            """;

    private static final String SLOTS_SQL = """
            SELECT id, section_id, topic_id, title, notes, duration_minutes
              FROM event_slot
             WHERE section_id IN (:ids)
             ORDER BY section_id, "position"
            """;

    private static final String PRESENTERS_SQL = """
            SELECT slot_id, user_id FROM event_slot_presenter WHERE slot_id IN (:ids)
            """;

    private static final String TAGS_SQL = """
            SELECT slot_id, tag FROM event_slot_tag WHERE slot_id IN (:ids)
            """;
}
