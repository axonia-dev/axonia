package dev.axonia.mentoring.adapter.out.persistence;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.application.port.out.MeetingRepository;
import dev.axonia.mentoring.domain.PlannedMeeting;

@Repository
public class MeetingRepositoryImpl implements MeetingRepository {

    private static final String HELD = """
            EXISTS (SELECT 1 FROM mentoring_note n
                     WHERE n.organization_id = m.organization_id AND n.pairing_id = m.pairing_id
                       AND n.meeting_on = m.meeting_on)""";

    private static final String SELECT = "SELECT m.id, m.pairing_id, m.meeting_on, m.planned_by, m.planned_at, " + HELD
            + " AS held FROM mentoring_meeting m";

    private static final String INSERT_SQL = """
            INSERT INTO mentoring_meeting (organization_id, pairing_id, meeting_on, planned_by, planned_at)
            VALUES (:org, :pairingId, :on, :plannedBy, :plannedAt)
            RETURNING id
            """;

    private static final String UPDATE_SQL = """
            UPDATE mentoring_meeting SET meeting_on = :on, planned_by = :plannedBy, planned_at = :plannedAt
             WHERE organization_id = :org AND pairing_id = :pairingId AND id = :id
            """;

    private static final String UNLINK_TOPICS_SQL = """
            DELETE FROM mentoring_meeting_topic WHERE organization_id = :org AND meeting_id = :id
            """;

    private static final String LINK_TOPIC_SQL = """
            INSERT INTO mentoring_meeting_topic (organization_id, meeting_id, topic_id)
            SELECT :org, m.id, t.id
              FROM mentoring_meeting m
              JOIN mentoring_topic t ON t.pairing_id = m.pairing_id AND t.organization_id = m.organization_id
             WHERE m.organization_id = :org AND m.id = :id AND t.id = :topicId
            """;

    private static final String DELETE_SQL = """
            DELETE FROM mentoring_meeting WHERE organization_id = :org AND pairing_id = :pairingId AND id = :id
            """;

    private static final String FIND_SQL = SELECT + " WHERE m.organization_id = :org AND m.id = :id";

    private static final String OF_PAIRING_SQL = SELECT
            + " WHERE m.organization_id = :org AND m.pairing_id = :pairingId ORDER BY m.meeting_on";

    private static final String OF_PAIRINGS_SQL = SELECT
            + " WHERE m.organization_id = :org AND m.pairing_id IN (:pairingIds) ORDER BY m.meeting_on";

    private static final String NOT_HELD_BETWEEN_SQL = SELECT + """
             JOIN mentoring_pairing p ON p.id = m.pairing_id AND p.organization_id = m.organization_id
            WHERE m.organization_id = :org AND p.ended_at IS NULL AND m.meeting_on BETWEEN :from AND :to
              AND NOT\s""" + HELD + " ORDER BY m.meeting_on, m.id";

    private static final String DELETE_UPCOMING_SQL = """
            DELETE FROM mentoring_meeting m
             WHERE m.organization_id = :org AND m.pairing_id = :pairingId AND m.meeting_on >= :today
               AND NOT\s""" + HELD;

    private static final String TOPICS_SQL = """
            SELECT mt.meeting_id, mt.topic_id
              FROM mentoring_meeting_topic mt
             WHERE mt.organization_id = :org AND mt.meeting_id IN (:ids)
             ORDER BY mt.meeting_id, mt.topic_id
            """;

    private static final RowMapper<PlannedMeeting> MEETING_MAPPER = (rs, i) -> PlannedMeeting.builder()
            .id(rs.getLong("id")).pairingId(rs.getLong("pairing_id")).on(rs.getObject("meeting_on", LocalDate.class))
            .topicIds(List.of()).plannedBy(rs.getString("planned_by")).plannedAt(Rows.instant(rs, "planned_at"))
            .held(rs.getBoolean("held")).build();

    private final NamedParameterJdbcTemplate jdbc;

    MeetingRepositoryImpl(NamedParameterJdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public long insert(String org, PlannedMeeting meeting) {

        var id = Objects.requireNonNull(jdbc.queryForObject(INSERT_SQL, written(org, meeting), Long.class));
        setTopics(org, id, meeting.topicIds());
        return id;
    }

    @Override
    @Transactional
    public void update(String org, PlannedMeeting meeting) {

        jdbc.update(UPDATE_SQL, written(org, meeting).addValue("id", meeting.id()));
        jdbc.update(UNLINK_TOPICS_SQL, new MapSqlParameterSource("org", org).addValue("id", meeting.id()));
        setTopics(org, meeting.id(), meeting.topicIds());
    }

    @Override
    @Transactional
    public void delete(String org, long pairingId, long meetingId) {

        jdbc.update(DELETE_SQL,
                new MapSqlParameterSource("org", org).addValue("pairingId", pairingId).addValue("id", meetingId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlannedMeeting> find(String org, long meetingId) {

        var found = jdbc.query(FIND_SQL, new MapSqlParameterSource("org", org).addValue("id", meetingId),
                MEETING_MAPPER);
        return withTopics(org, found).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlannedMeeting> meetingsOf(String org, long pairingId) {

        var found = jdbc.query(OF_PAIRING_SQL, new MapSqlParameterSource("org", org).addValue("pairingId", pairingId),
                MEETING_MAPPER);
        return withTopics(org, found);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<PlannedMeeting>> meetingsOf(String org, Collection<Long> pairingIds) {

        var grouped = new LinkedHashMap<Long, List<PlannedMeeting>>();
        if (pairingIds.isEmpty()) {
            return grouped;
        }
        var found = jdbc.query(OF_PAIRINGS_SQL,
                new MapSqlParameterSource("org", org).addValue("pairingIds", pairingIds), MEETING_MAPPER);
        withTopics(org, found)
                .forEach(meeting -> grouped.computeIfAbsent(meeting.pairingId(), id -> new ArrayList<>()).add(meeting));
        return grouped;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlannedMeeting> notHeldBetween(String org, LocalDate from, LocalDate to) {

        var found = jdbc.query(NOT_HELD_BETWEEN_SQL,
                new MapSqlParameterSource("org", org).addValue("from", from).addValue("to", to), MEETING_MAPPER);
        return withTopics(org, found);
    }

    @Override
    @Transactional
    public void deleteUpcoming(String org, long pairingId, LocalDate today) {

        jdbc.update(DELETE_UPCOMING_SQL,
                new MapSqlParameterSource("org", org).addValue("pairingId", pairingId).addValue("today", today));
    }

    private static MapSqlParameterSource written(String org, PlannedMeeting meeting) {

        return new MapSqlParameterSource("org", org).addValue("pairingId", meeting.pairingId())
                .addValue("on", meeting.on()).addValue("plannedBy", meeting.plannedBy())
                .addValue("plannedAt", Rows.timestamp(meeting.plannedAt()));
    }

    private void setTopics(String org, long meetingId, List<Long> topicIds) {

        jdbc.batchUpdate(LINK_TOPIC_SQL, topicIds.stream().map(
                topicId -> new MapSqlParameterSource("org", org).addValue("id", meetingId).addValue("topicId", topicId))
                .toArray(MapSqlParameterSource[]::new));
    }

    private List<PlannedMeeting> withTopics(String org, List<PlannedMeeting> meetings) {

        if (meetings.isEmpty()) {
            return meetings;
        }
        var topics = new LinkedHashMap<Long, List<Long>>();
        jdbc.query(TOPICS_SQL, new MapSqlParameterSource("org", org).addValue("ids",
                meetings.stream().map(PlannedMeeting::id).toList()), rs -> {
                    topics.computeIfAbsent(rs.getLong("meeting_id"), id -> new ArrayList<>())
                            .add(rs.getLong("topic_id"));
                });
        return meetings.stream()
                .map(meeting -> meeting.toBuilder().topicIds(topics.getOrDefault(meeting.id(), List.of())).build())
                .toList();
    }
}
