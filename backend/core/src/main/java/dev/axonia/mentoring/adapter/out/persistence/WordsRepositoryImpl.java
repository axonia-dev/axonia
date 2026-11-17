package dev.axonia.mentoring.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.application.port.out.WordsRepository;
import dev.axonia.mentoring.domain.Words;
import dev.axonia.mentoring.domain.WordsForMentor;

@Repository
public class WordsRepositoryImpl implements WordsRepository {

    private static final String FIND_SQL = """
            SELECT w.pairing_id, w.body, w.written_at, w.updated_at, w.opened_at
              FROM mentoring_words w
             WHERE w.organization_id = :org AND w.pairing_id = :pairingId
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO mentoring_words (pairing_id, organization_id, body, written_at)
            VALUES (:pairingId, :org, :body, :writtenAt)
            ON CONFLICT (pairing_id) DO UPDATE
               SET body = EXCLUDED.body,
                   updated_at = CASE WHEN mentoring_words.body = EXCLUDED.body THEN mentoring_words.updated_at
                                     ELSE EXCLUDED.written_at END,
                   opened_at = CASE WHEN mentoring_words.body = EXCLUDED.body THEN mentoring_words.opened_at END
            RETURNING (xmax = 0)
            """;

    private static final String MARK_OPENED_SQL = """
            UPDATE mentoring_words SET opened_at = :at
             WHERE organization_id = :org AND pairing_id = :pairingId AND opened_at IS NULL
            """;

    private static final String DELETE_SQL = """
            DELETE FROM mentoring_words WHERE organization_id = :org AND pairing_id = :pairingId
            """;

    private static final String TO_MENTOR_SQL = """
            SELECT w.pairing_id, p.community_id, p.mentee_user_id, w.body, w.written_at, w.updated_at, w.opened_at,
                   p.started_at, p.ended_at
              FROM mentoring_words w
              JOIN mentoring_pairing p ON p.organization_id = w.organization_id AND p.id = w.pairing_id
             WHERE w.organization_id = :org AND p.mentor_user_id = :mentorId
             ORDER BY w.written_at DESC, w.pairing_id DESC
            """;

    private static final RowMapper<Words> MAPPER = (rs, i) -> Words.builder().pairingId(rs.getLong("pairing_id"))
            .body(rs.getString("body")).writtenAt(Rows.instant(rs, "written_at"))
            .updatedAt(Rows.instant(rs, "updated_at")).openedAt(Rows.instant(rs, "opened_at")).build();

    private static final RowMapper<WordsForMentor> FOR_MENTOR_MAPPER = (rs, i) -> WordsForMentor.builder()
            .pairingId(rs.getLong("pairing_id")).communityId(rs.getLong("community_id"))
            .menteeUserId(rs.getString("mentee_user_id")).body(rs.getString("body"))
            .writtenAt(Rows.instant(rs, "written_at")).updatedAt(Rows.instant(rs, "updated_at"))
            .openedAt(Rows.instant(rs, "opened_at")).startedAt(Rows.instant(rs, "started_at"))
            .endedAt(Rows.instant(rs, "ended_at")).build();

    private final NamedParameterJdbcTemplate jdbc;

    WordsRepositoryImpl(NamedParameterJdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Words> find(String org, long pairingId) {

        return jdbc.query(FIND_SQL, scope(org, pairingId), MAPPER).stream().findFirst();
    }

    @Override
    @Transactional
    public boolean upsert(String org, Words words) {

        var created = jdbc.queryForObject(UPSERT_SQL, scope(org, words.pairingId()).addValue("body", words.body())
                .addValue("writtenAt", Rows.timestamp(words.writtenAt())), Boolean.class);
        return Objects.requireNonNull(created);
    }

    @Override
    @Transactional
    public void markOpened(String org, long pairingId, Instant at) {

        jdbc.update(MARK_OPENED_SQL, scope(org, pairingId).addValue("at", Rows.timestamp(at)));
    }

    @Override
    @Transactional
    public void delete(String org, long pairingId) {

        jdbc.update(DELETE_SQL, scope(org, pairingId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WordsForMentor> toMentor(String org, String mentorId) {

        return jdbc.query(TO_MENTOR_SQL, new MapSqlParameterSource("org", org).addValue("mentorId", mentorId),
                FOR_MENTOR_MAPPER);
    }

    private static MapSqlParameterSource scope(String org, long pairingId) {

        return new MapSqlParameterSource("org", org).addValue("pairingId", pairingId);
    }
}
