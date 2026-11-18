package dev.axonia.mentoring.adapter.out.persistence;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.application.port.out.JournalRepository;
import dev.axonia.mentoring.domain.Note;
import dev.axonia.mentoring.domain.NoteTally;
import dev.axonia.mentoring.domain.Pointer;
import dev.axonia.mentoring.domain.PointerKind;
import dev.axonia.mentoring.domain.Topic;
import dev.axonia.mentoring.domain.TopicBoard;
import dev.axonia.mentoring.domain.TopicState;

@Repository
public class JournalRepositoryImpl implements JournalRepository {

    private static final String INSERT_TOPIC_SQL = """
            INSERT INTO mentoring_topic (organization_id, pairing_id, label, area, state, "position", created_at)
            VALUES (:org, :pairingId, :label, :area, :state, :position, :createdAt)
            RETURNING id
            """;

    private static final String UPDATE_TOPIC_SQL = """
            UPDATE mentoring_topic SET label = :label, area = :area, state = :state, "position" = :position
             WHERE organization_id = :org AND pairing_id = :pairingId AND id = :id
            """;

    private static final String PLACE_TOPIC_SQL = """
            UPDATE mentoring_topic SET state = :state, "position" = :position
             WHERE organization_id = :org AND pairing_id = :pairingId AND id = :id
            """;

    private static final String DELETE_TOPIC_SQL = """
            DELETE FROM mentoring_topic WHERE organization_id = :org AND pairing_id = :pairingId AND id = :id
            """;

    private static final String TOPICS_OF_SQL = """
            SELECT t.id, t.pairing_id, t.label, t.area, t.state, t."position", t.created_at
              FROM mentoring_topic t
             WHERE t.organization_id = :org AND t.pairing_id = :pairingId
             ORDER BY t."position", t.id
            """;

    private static final String TOPICS_OF_PAIRINGS_SQL = """
            SELECT t.id, t.pairing_id, t.label, t.area, t.state, t."position", t.created_at
              FROM mentoring_topic t
             WHERE t.organization_id = :org AND t.pairing_id IN (:pairingIds)
            """;

    private static final String TALLY_OF_PAIRINGS_SQL = """
            SELECT n.pairing_id, count(*) AS notes, count(DISTINCT n.meeting_on) AS meetings,
                   max(n.meeting_on) AS last_met_on
              FROM mentoring_note n
             WHERE n.organization_id = :org AND n.pairing_id IN (:pairingIds)
             GROUP BY n.pairing_id
            """;

    private static final String INSERT_NOTE_SQL = """
            INSERT INTO mentoring_note (organization_id, pairing_id, author_user_id, body, meeting_on, written_at,
                                        edited_at)
            VALUES (:org, :pairingId, :authorId, :body, :meetingOn, :writtenAt, :editedAt)
            RETURNING id
            """;

    private static final String UPDATE_NOTE_SQL = """
            UPDATE mentoring_note SET body = :body, meeting_on = :meetingOn, edited_at = :editedAt
             WHERE organization_id = :org AND pairing_id = :pairingId AND id = :id
            """;

    private static final String DELETE_NOTE_SQL = """
            DELETE FROM mentoring_note WHERE organization_id = :org AND pairing_id = :pairingId AND id = :id
            """;

    private static final String HAS_NOTE_ON_SQL = """
            SELECT EXISTS (SELECT 1 FROM mentoring_note
                            WHERE organization_id = :org AND pairing_id = :pairingId AND meeting_on = :day)
            """;

    private static final String NOTES_OF_SQL = """
            SELECT n.id, n.pairing_id, n.author_user_id, n.body, n.meeting_on, n.written_at, n.edited_at
              FROM mentoring_note n
             WHERE n.organization_id = :org AND n.pairing_id = :pairingId
             ORDER BY n.meeting_on DESC, n.written_at DESC, n.id DESC
            """;

    private static final String NOTE_TOPICS_OF_SQL = """
            SELECT nt.note_id, nt.topic_id
              FROM mentoring_note_topic nt
              JOIN mentoring_note n ON n.id = nt.note_id AND n.organization_id = nt.organization_id
             WHERE nt.organization_id = :org AND n.pairing_id = :pairingId
             ORDER BY nt.note_id, nt.topic_id
            """;

    private static final String UNLINK_NOTE_TOPICS_SQL = """
            DELETE FROM mentoring_note_topic nt
             USING mentoring_note n
             WHERE n.id = nt.note_id AND n.organization_id = nt.organization_id
               AND nt.organization_id = :org AND n.pairing_id = :pairingId AND nt.note_id = :noteId
            """;

    private static final String LINK_NOTE_TOPIC_SQL = """
            INSERT INTO mentoring_note_topic (organization_id, note_id, topic_id)
            SELECT :org, n.id, t.id
              FROM mentoring_note n
              JOIN mentoring_topic t ON t.pairing_id = n.pairing_id AND t.organization_id = n.organization_id
             WHERE n.organization_id = :org AND n.pairing_id = :pairingId AND n.id = :noteId AND t.id = :topicId
            """;

    private static final String INSERT_POINTER_SQL = """
            INSERT INTO mentoring_pointer (organization_id, pairing_id, topic_id, note_id, kind, target_id, url,
                                           created_at)
            VALUES (:org, :pairingId, :topicId, :noteId, :kind, :targetId, :url, :createdAt)
            RETURNING id
            """;

    private static final String DELETE_POINTER_SQL = """
            DELETE FROM mentoring_pointer WHERE organization_id = :org AND pairing_id = :pairingId AND id = :id
            """;

    private static final String POINTERS_OF_SQL = """
            SELECT p.id, p.pairing_id, p.topic_id, p.note_id, p.kind, p.target_id, p.url, p.created_at
              FROM mentoring_pointer p
             WHERE p.organization_id = :org AND p.pairing_id = :pairingId
             ORDER BY p.created_at, p.id
            """;

    private static final String RENAME_AREA_SQL = """
            UPDATE mentoring_topic t SET area = :to
              FROM mentoring_pairing p
             WHERE p.organization_id = t.organization_id AND p.id = t.pairing_id
               AND t.organization_id = :org AND p.community_id = :communityId AND t.area = :from
            """;

    private static final String UNLINK_AREA_SQL = """
            UPDATE mentoring_topic t SET area = NULL
              FROM mentoring_pairing p
             WHERE p.organization_id = t.organization_id AND p.id = t.pairing_id
               AND t.organization_id = :org AND p.community_id = :communityId AND t.area = :area
            """;

    private static final RowMapper<Topic> TOPIC_MAPPER = (rs, i) -> Topic.builder().id(rs.getLong("id"))
            .pairingId(rs.getLong("pairing_id")).label(rs.getString("label")).area(rs.getString("area"))
            .state(TopicState.valueOf(rs.getString("state"))).position(rs.getInt("position"))
            .createdAt(Rows.instant(rs, "created_at")).build();

    private static final RowMapper<Note> NOTE_MAPPER = (rs, i) -> Note.builder().id(rs.getLong("id"))
            .pairingId(rs.getLong("pairing_id")).authorUserId(rs.getString("author_user_id")).body(rs.getString("body"))
            .meetingOn(rs.getObject("meeting_on", LocalDate.class)).writtenAt(Rows.instant(rs, "written_at"))
            .editedAt(Rows.instant(rs, "edited_at")).build();

    private static final RowMapper<Pointer> POINTER_MAPPER = (rs, i) -> Pointer.builder().id(rs.getLong("id"))
            .pairingId(rs.getLong("pairing_id")).topicId(Rows.nullableLong(rs, "topic_id"))
            .noteId(Rows.nullableLong(rs, "note_id")).kind(PointerKind.valueOf(rs.getString("kind")))
            .targetId(Rows.nullableLong(rs, "target_id")).url(rs.getString("url"))
            .createdAt(Rows.instant(rs, "created_at")).build();

    private final NamedParameterJdbcTemplate jdbc;

    JournalRepositoryImpl(NamedParameterJdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public long insertTopic(String org, Topic topic) {

        return Objects.requireNonNull(jdbc.queryForObject(INSERT_TOPIC_SQL,
                topicParams(org, topic).addValue("createdAt", Rows.timestamp(topic.createdAt())), Long.class));
    }

    @Override
    @Transactional
    public void updateTopic(String org, Topic topic) {

        jdbc.update(UPDATE_TOPIC_SQL, topicParams(org, topic).addValue("id", topic.id()));
    }

    @Override
    @Transactional
    public void placeTopics(String org, long pairingId, List<Topic> changed) {

        jdbc.batchUpdate(PLACE_TOPIC_SQL,
                changed.stream().map(t -> owned(org, pairingId, t.id()).addValue("state", t.state().name())
                        .addValue("position", t.position())).toArray(SqlParameterSource[]::new));
    }

    @Override
    @Transactional
    public void deleteTopic(String org, long pairingId, long topicId) {

        jdbc.update(DELETE_TOPIC_SQL, owned(org, pairingId, topicId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Topic> topicsOf(String org, long pairingId) {

        return jdbc.query(TOPICS_OF_SQL, pairing(org, pairingId), TOPIC_MAPPER);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<Topic>> trailsOf(String org, Collection<Long> pairingIds) {

        if (pairingIds.isEmpty()) {
            return Map.of();
        }
        var topics = jdbc.query(TOPICS_OF_PAIRINGS_SQL, pairings(org, pairingIds), TOPIC_MAPPER);
        return topics.stream().collect(Collectors.groupingBy(Topic::pairingId,
                Collectors.collectingAndThen(Collectors.toList(), TopicBoard::trailOrder)));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, NoteTally> tallyOf(String org, Collection<Long> pairingIds) {

        if (pairingIds.isEmpty()) {
            return Map.of();
        }
        var tallies = new HashMap<Long, NoteTally>();
        jdbc.query(TALLY_OF_PAIRINGS_SQL, pairings(org, pairingIds), rs -> {
            tallies.put(rs.getLong("pairing_id"), new NoteTally(rs.getInt("notes"), rs.getInt("meetings"),
                    rs.getObject("last_met_on", LocalDate.class)));
        });
        return tallies;
    }

    @Override
    @Transactional
    public long insertNote(String org, Note note) {

        return Objects.requireNonNull(jdbc.queryForObject(INSERT_NOTE_SQL,
                pairing(org, note.pairingId()).addValue("authorId", note.authorUserId()).addValue("body", note.body())
                        .addValue("meetingOn", note.meetingOn()).addValue("writtenAt", Rows.timestamp(note.writtenAt()))
                        .addValue("editedAt", Rows.timestamp(note.editedAt())),
                Long.class));
    }

    @Override
    @Transactional
    public void updateNote(String org, Note note) {

        jdbc.update(UPDATE_NOTE_SQL, owned(org, note.pairingId(), note.id()).addValue("body", note.body())
                .addValue("meetingOn", note.meetingOn()).addValue("editedAt", Rows.timestamp(note.editedAt())));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> noteTopicsOf(String org, long pairingId) {

        var linked = new LinkedHashMap<Long, List<Long>>();
        jdbc.query(NOTE_TOPICS_OF_SQL, pairing(org, pairingId), rs -> {
            linked.computeIfAbsent(rs.getLong("note_id"), id -> new ArrayList<>()).add(rs.getLong("topic_id"));
        });
        return linked;
    }

    @Override
    @Transactional
    public void setNoteTopics(String org, long pairingId, long noteId, List<Long> topicIds) {

        jdbc.update(UNLINK_NOTE_TOPICS_SQL, pairing(org, pairingId).addValue("noteId", noteId));
        jdbc.batchUpdate(LINK_NOTE_TOPIC_SQL,
                topicIds.stream()
                        .map(topicId -> pairing(org, pairingId).addValue("noteId", noteId).addValue("topicId", topicId))
                        .toArray(SqlParameterSource[]::new));
    }

    @Override
    @Transactional
    public void deleteNote(String org, long pairingId, long noteId) {

        jdbc.update(DELETE_NOTE_SQL, owned(org, pairingId, noteId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Note> notesOf(String org, long pairingId) {

        return jdbc.query(NOTES_OF_SQL, pairing(org, pairingId), NOTE_MAPPER);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasNoteOn(String org, long pairingId, LocalDate day) {

        return Boolean.TRUE.equals(
                jdbc.queryForObject(HAS_NOTE_ON_SQL, pairing(org, pairingId).addValue("day", day), Boolean.class));
    }

    @Override
    @Transactional
    public long insertPointer(String org, Pointer pointer) {

        return Objects.requireNonNull(jdbc.queryForObject(INSERT_POINTER_SQL,
                pairing(org, pointer.pairingId()).addValue("topicId", pointer.topicId())
                        .addValue("noteId", pointer.noteId()).addValue("kind", pointer.kind().name())
                        .addValue("targetId", pointer.targetId()).addValue("url", pointer.url())
                        .addValue("createdAt", Rows.timestamp(pointer.createdAt())),
                Long.class));
    }

    @Override
    @Transactional
    public void deletePointer(String org, long pairingId, long pointerId) {

        jdbc.update(DELETE_POINTER_SQL, owned(org, pairingId, pointerId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pointer> pointersOf(String org, long pairingId) {

        return jdbc.query(POINTERS_OF_SQL, pairing(org, pairingId), POINTER_MAPPER);
    }

    @Override
    @Transactional
    public void renameArea(String org, long communityId, String from, String to) {

        jdbc.update(RENAME_AREA_SQL, new MapSqlParameterSource("org", org).addValue("communityId", communityId)
                .addValue("from", from).addValue("to", to));
    }

    @Override
    @Transactional
    public void unlinkArea(String org, long communityId, String area) {

        jdbc.update(UNLINK_AREA_SQL,
                new MapSqlParameterSource("org", org).addValue("communityId", communityId).addValue("area", area));
    }

    private static MapSqlParameterSource topicParams(String org, Topic topic) {

        return pairing(org, topic.pairingId()).addValue("label", topic.label()).addValue("area", topic.area())
                .addValue("state", topic.state().name()).addValue("position", topic.position());
    }

    private static MapSqlParameterSource pairing(String org, long pairingId) {

        return new MapSqlParameterSource("org", org).addValue("pairingId", pairingId);
    }

    private static MapSqlParameterSource pairings(String org, Collection<Long> pairingIds) {

        return new MapSqlParameterSource("org", org).addValue("pairingIds", pairingIds);
    }

    private static MapSqlParameterSource owned(String org, long pairingId, long id) {

        return pairing(org, pairingId).addValue("id", id);
    }
}
