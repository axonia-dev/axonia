package dev.axonia.mentoring.adapter.out.persistence;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.domain.EndReason;
import dev.axonia.mentoring.domain.MentorHistory;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Showing;
import dev.axonia.mentoring.domain.Visibility;

@Repository
public class PairingRepositoryImpl implements PairingRepository {

    private static final String SELECT = """
            SELECT p.id, p.community_id, p.mentor_user_id, p.mentee_user_id, p.started_at, p.ended_at, p.end_reason,
                   p.visibility, p.mentor_said_yes, p.mentee_said_yes
              FROM mentoring_pairing p
            """;

    private static final String INSERT_SQL = """
            INSERT INTO mentoring_pairing (organization_id, community_id, mentor_user_id, mentee_user_id, started_at,
                                           ended_at, end_reason, visibility, mentor_said_yes, mentee_said_yes)
            VALUES (:org, :communityId, :mentorId, :menteeId, :startedAt, :endedAt, :endReason, :visibility,
                    :mentorSaidYes, :menteeSaidYes)
            RETURNING id
            """;

    private static final String FIND_SQL = SELECT + " WHERE p.organization_id = :org AND p.id = :id";

    private static final String FIND_FOR_WRITE_SQL = FIND_SQL + " FOR NO KEY UPDATE";

    private static final String INVOLVING_SQL = SELECT + """
             WHERE p.organization_id = :org AND (p.mentor_user_id = :userId OR p.mentee_user_id = :userId)
             ORDER BY p.started_at DESC, p.id DESC
            """;

    private static final String ONGOING_INVOLVING_SQL = SELECT + """
             WHERE p.organization_id = :org AND p.ended_at IS NULL
               AND (p.mentor_user_id = :userId OR p.mentee_user_id = :userId)
               AND (CAST(:communityId AS bigint) IS NULL OR p.community_id = :communityId)
             ORDER BY p.started_at DESC, p.id DESC
            """;

    private static final String SHOWN_IN_SQL = SELECT + """
             WHERE p.organization_id = :org AND p.community_id = :communityId AND p.ended_at IS NULL
               AND p.visibility = 'SHOWN' AND p.mentor_user_id IS NOT NULL AND p.mentee_user_id IS NOT NULL
            """;

    private static final String MENTOR_HISTORIES_IN_SQL = """
            SELECT mentor_user_id,
                   bool_or(ended_at IS NULL AND visibility = 'SHOWN') AS ongoing_shown,
                   bool_or(ended_at IS NOT NULL AND visibility = 'SHOWN') AS ended_shown,
                   bool_or(ended_at IS NOT NULL AND visibility = 'ASKING') AS ended_unshown
              FROM mentoring_pairing
             WHERE organization_id = :org AND community_id = :communityId
               AND mentor_user_id IS NOT NULL AND mentee_user_id IS NOT NULL
               AND visibility <> 'PRIVATE'
               AND (ended_at IS NOT NULL OR visibility = 'SHOWN')
             GROUP BY mentor_user_id
            """;

    private static final String SAVE_SHOWING_SQL = """
            UPDATE mentoring_pairing
               SET visibility = :visibility, mentor_said_yes = :mentorSaidYes, mentee_said_yes = :menteeSaidYes
             WHERE organization_id = :org AND id = :id
            """;

    private static final String ONGOING_AS_MENTOR_SQL = """
            SELECT count(*) FROM mentoring_pairing
             WHERE organization_id = :org AND community_id = :communityId AND mentor_user_id = :mentorId
               AND ended_at IS NULL
            """;

    private static final String ONGOING_AS_MENTOR_IN_SQL = """
            SELECT mentor_user_id, count(*) AS ongoing FROM mentoring_pairing
             WHERE organization_id = :org AND community_id = :communityId AND ended_at IS NULL
               AND mentor_user_id IS NOT NULL
             GROUP BY mentor_user_id
            """;

    private static final String ANY_MENTORING_INVOLVING_SQL = """
            SELECT EXISTS (SELECT 1 FROM mentoring_pairing
                            WHERE organization_id = :org AND (mentor_user_id = :userId OR mentee_user_id = :userId))
                OR EXISTS (SELECT 1 FROM mentoring_proposal
                            WHERE organization_id = :org AND (mentor_user_id = :userId OR mentee_user_id = :userId))
            """;

    private static final String END_SQL = """
            UPDATE mentoring_pairing SET ended_at = :at, end_reason = :reason
             WHERE organization_id = :org AND id = :id AND ended_at IS NULL
            """;

    private static final String LOCK_MEMBER_SQL = """
            SELECT pg_advisory_xact_lock(hashtextextended(
                       'mentoring_member:' || :org || ':' || CAST(:communityId AS text) || ':' || :userId, 0))
            """;

    private static final RowMapper<Pairing> MAPPER = (rs, i) -> Pairing.builder().id(rs.getLong("id"))
            .communityId(rs.getLong("community_id")).mentorUserId(rs.getString("mentor_user_id"))
            .menteeUserId(rs.getString("mentee_user_id")).startedAt(Rows.instant(rs, "started_at"))
            .endedAt(Rows.instant(rs, "ended_at")).endReason(endReason(rs.getString("end_reason")))
            .showing(new Showing(Visibility.valueOf(rs.getString("visibility")), rs.getBoolean("mentor_said_yes"),
                    rs.getBoolean("mentee_said_yes")))
            .build();

    private final NamedParameterJdbcTemplate jdbc;

    PairingRepositoryImpl(NamedParameterJdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public long insert(String org, Pairing pairing) {

        var params = new MapSqlParameterSource("org", org).addValue("communityId", pairing.communityId())
                .addValue("mentorId", pairing.mentorUserId()).addValue("menteeId", pairing.menteeUserId())
                .addValue("startedAt", Rows.timestamp(pairing.startedAt()))
                .addValue("endedAt", Rows.timestamp(pairing.endedAt()))
                .addValue("endReason", pairing.endReason() == null ? null : pairing.endReason().name());
        return Objects
                .requireNonNull(jdbc.queryForObject(INSERT_SQL, withShowing(params, pairing.showing()), Long.class));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pairing> shownIn(String org, long communityId) {

        return jdbc.query(SHOWN_IN_SQL, new MapSqlParameterSource("org", org).addValue("communityId", communityId),
                MAPPER);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MentorHistory> mentorHistoriesIn(String org, long communityId) {

        return jdbc.query(MENTOR_HISTORIES_IN_SQL,
                new MapSqlParameterSource("org", org).addValue("communityId", communityId),
                (rs, i) -> new MentorHistory(rs.getString("mentor_user_id"), rs.getBoolean("ongoing_shown"),
                        rs.getBoolean("ended_shown"), rs.getBoolean("ended_unshown")));
    }

    @Override
    @Transactional
    public void saveShowing(String org, long pairingId, Showing showing) {

        jdbc.update(SAVE_SHOWING_SQL,
                withShowing(new MapSqlParameterSource("org", org).addValue("id", pairingId), showing));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Pairing> find(String org, long id) {

        return jdbc.query(FIND_SQL, new MapSqlParameterSource("org", org).addValue("id", id), MAPPER).stream()
                .findFirst();
    }

    @Override
    @Transactional
    public Optional<Pairing> findForWrite(String org, long id) {

        return jdbc.query(FIND_FOR_WRITE_SQL, new MapSqlParameterSource("org", org).addValue("id", id), MAPPER).stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pairing> involving(String org, String userId) {

        return jdbc.query(INVOLVING_SQL, new MapSqlParameterSource("org", org).addValue("userId", userId), MAPPER);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pairing> ongoingInvolving(String org, Long communityId, String userId) {

        return jdbc.query(ONGOING_INVOLVING_SQL,
                new MapSqlParameterSource("org", org).addValue("communityId", communityId).addValue("userId", userId),
                MAPPER);
    }

    @Override
    @Transactional(readOnly = true)
    public int ongoingAsMentor(String org, long communityId, String mentorId) {

        return Objects.requireNonNull(jdbc.queryForObject(ONGOING_AS_MENTOR_SQL, new MapSqlParameterSource("org", org)
                .addValue("communityId", communityId).addValue("mentorId", mentorId), Integer.class));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> ongoingAsMentorIn(String org, long communityId) {

        var counts = new HashMap<String, Integer>();
        jdbc.query(ONGOING_AS_MENTOR_IN_SQL, new MapSqlParameterSource("org", org).addValue("communityId", communityId),
                rs -> {
                    counts.put(rs.getString("mentor_user_id"), rs.getInt("ongoing"));
                });
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean anyMentoringInvolving(String org, String userId) {

        return Boolean.TRUE.equals(jdbc.queryForObject(ANY_MENTORING_INVOLVING_SQL,
                new MapSqlParameterSource("org", org).addValue("userId", userId), Boolean.class));
    }

    @Override
    @Transactional
    public boolean end(String org, long id, EndReason reason, Instant at) {

        return jdbc.update(END_SQL, new MapSqlParameterSource("org", org).addValue("id", id)
                .addValue("reason", reason.name()).addValue("at", Rows.timestamp(at))) == 1;
    }

    @Override
    @Transactional
    public void lockMember(String org, long communityId, String userId) {

        jdbc.query(LOCK_MEMBER_SQL,
                new MapSqlParameterSource("org", org).addValue("communityId", communityId).addValue("userId", userId),
                rs -> {
                });
    }

    private static EndReason endReason(String value) {

        return value == null ? null : EndReason.valueOf(value);
    }

    private static MapSqlParameterSource withShowing(MapSqlParameterSource params, Showing showing) {

        return params.addValue("visibility", showing.visibility().name())
                .addValue("mentorSaidYes", showing.mentorSaidYes()).addValue("menteeSaidYes", showing.menteeSaidYes());
    }
}
