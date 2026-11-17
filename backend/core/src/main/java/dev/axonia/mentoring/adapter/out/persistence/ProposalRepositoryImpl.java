package dev.axonia.mentoring.adapter.out.persistence;

import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.application.port.out.ProposalRepository;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalStatus;
import dev.axonia.mentoring.domain.Role;

@Repository
public class ProposalRepositoryImpl implements ProposalRepository {

    private static final String SELECT = """
            SELECT p.id, p.community_id, p.mentor_user_id, p.mentee_user_id, p.proposed_by, p.topics, p.message,
                   p.status, p.created_at, p.answered_at, p.reopened_at
              FROM mentoring_proposal p
            """;

    private static final String INSERT_SQL = """
            INSERT INTO mentoring_proposal (organization_id, community_id, mentor_user_id, mentee_user_id, proposed_by,
                                            topics, message, status, created_at, answered_at, reopened_at)
            VALUES (:org, :communityId, :mentorId, :menteeId, :proposedBy, :topics, :message, :status, :createdAt,
                    :answeredAt, :reopenedAt)
            RETURNING id
            """;

    private static final String FIND_SQL = SELECT + " WHERE p.organization_id = :org AND p.id = :id";

    private static final String BETWEEN_SQL = SELECT + """
             WHERE p.organization_id = :org AND p.community_id = :communityId
               AND ((p.mentor_user_id = :one AND p.mentee_user_id = :other)
                    OR (p.mentor_user_id = :other AND p.mentee_user_id = :one))
             ORDER BY p.created_at DESC, p.id DESC
            """;

    private static final String INVOLVING_SQL = SELECT + """
             WHERE p.organization_id = :org AND (p.mentor_user_id = :userId OR p.mentee_user_id = :userId)
             ORDER BY p.created_at DESC, p.id DESC
            """;

    private static final String INVOLVING_IN_SQL = SELECT + """
             WHERE p.organization_id = :org AND p.community_id = :communityId
               AND (p.mentor_user_id = :userId OR p.mentee_user_id = :userId)
             ORDER BY p.created_at DESC, p.id DESC
            """;

    private static final String ANSWER_SQL = """
            UPDATE mentoring_proposal SET status = :status, answered_at = :at
             WHERE organization_id = :org AND id = :id AND status = 'OPEN'
            """;

    private static final String REOPEN_SQL = """
            UPDATE mentoring_proposal SET status = 'OPEN', answered_at = NULL, reopened_at = :at
             WHERE organization_id = :org AND id = :id AND status = 'DECLINED'
            """;

    private static final String WITHDRAW_BETWEEN_SQL = """
            UPDATE mentoring_proposal SET status = 'WITHDRAWN', answered_at = :at
             WHERE organization_id = :org AND community_id = :communityId AND status = 'OPEN' AND id <> :id
               AND ((mentor_user_id = :mentorId AND mentee_user_id = :menteeId)
                    OR (mentor_user_id = :menteeId AND mentee_user_id = :mentorId))
            """;

    private static final String WITHDRAW_SQL = """
            UPDATE mentoring_proposal SET status = 'WITHDRAWN', answered_at = :at
             WHERE organization_id = :org AND status = 'OPEN'
               AND (mentor_user_id = :userId OR mentee_user_id = :userId)
               AND (CAST(:communityId AS bigint) IS NULL OR community_id = :communityId)
            """;

    private static final RowMapper<Proposal> MAPPER = (rs, i) -> Proposal.builder().id(rs.getLong("id"))
            .communityId(rs.getLong("community_id")).mentorUserId(rs.getString("mentor_user_id"))
            .menteeUserId(rs.getString("mentee_user_id")).proposedBy(Role.valueOf(rs.getString("proposed_by")))
            .topics(List.of((String[]) rs.getArray("topics").getArray())).message(rs.getString("message"))
            .status(ProposalStatus.valueOf(rs.getString("status"))).createdAt(Rows.instant(rs, "created_at"))
            .answeredAt(Rows.instant(rs, "answered_at")).reopenedAt(Rows.instant(rs, "reopened_at")).build();

    private final NamedParameterJdbcTemplate jdbc;

    ProposalRepositoryImpl(NamedParameterJdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public long insert(String org, Proposal proposal) {

        var params = new MapSqlParameterSource("org", org).addValue("communityId", proposal.communityId())
                .addValue("mentorId", proposal.mentorUserId()).addValue("menteeId", proposal.menteeUserId())
                .addValue("proposedBy", proposal.proposedBy().name())
                .addValue("topics",
                        new SqlParameterValue(Types.ARRAY, "varchar", proposal.topics().toArray(String[]::new)))
                .addValue("message", proposal.message()).addValue("status", proposal.status().name())
                .addValue("createdAt", Rows.timestamp(proposal.createdAt()))
                .addValue("answeredAt", Rows.timestamp(proposal.answeredAt()))
                .addValue("reopenedAt", Rows.timestamp(proposal.reopenedAt()));
        return Objects.requireNonNull(jdbc.queryForObject(INSERT_SQL, params, Long.class));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Proposal> find(String org, long id) {

        return jdbc.query(FIND_SQL, new MapSqlParameterSource("org", org).addValue("id", id), MAPPER).stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Proposal> between(String org, long communityId, String one, String other) {

        var params = new MapSqlParameterSource("org", org).addValue("communityId", communityId).addValue("one", one)
                .addValue("other", other);
        return jdbc.query(BETWEEN_SQL, params, MAPPER);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Proposal> involving(String org, String userId) {

        return jdbc.query(INVOLVING_SQL, new MapSqlParameterSource("org", org).addValue("userId", userId), MAPPER);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Proposal> involvingIn(String org, long communityId, String userId) {

        return jdbc.query(INVOLVING_IN_SQL,
                new MapSqlParameterSource("org", org).addValue("communityId", communityId).addValue("userId", userId),
                MAPPER);
    }

    @Override
    @Transactional
    public boolean answer(String org, long id, ProposalStatus status, Instant at) {

        return jdbc.update(ANSWER_SQL, new MapSqlParameterSource("org", org).addValue("id", id)
                .addValue("status", status.name()).addValue("at", Rows.timestamp(at))) == 1;
    }

    @Override
    @Transactional
    public boolean reopen(String org, long id, Instant at) {

        return jdbc.update(REOPEN_SQL,
                new MapSqlParameterSource("org", org).addValue("id", id).addValue("at", Rows.timestamp(at))) == 1;
    }

    @Override
    @Transactional
    public void withdrawOthersBetween(String org, Proposal accepted, Instant at) {

        jdbc.update(WITHDRAW_BETWEEN_SQL,
                new MapSqlParameterSource("org", org).addValue("communityId", accepted.communityId())
                        .addValue("id", accepted.id()).addValue("mentorId", accepted.mentorUserId())
                        .addValue("menteeId", accepted.menteeUserId()).addValue("at", Rows.timestamp(at)));
    }

    @Override
    @Transactional
    public void withdrawOpenInvolving(String org, Long communityId, String userId, Instant at) {

        jdbc.update(WITHDRAW_SQL, new MapSqlParameterSource("org", org).addValue("communityId", communityId)
                .addValue("userId", userId).addValue("at", Rows.timestamp(at)));
    }
}
