package dev.axonia.events.adapter.out.health;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.domain.Cadence;
import dev.axonia.events.domain.CadenceUnit;
import dev.axonia.events.domain.FeedbackRules;
import dev.axonia.health.api.BucketCredit;
import dev.axonia.health.api.BucketTalks;
import dev.axonia.health.api.BucketedSeries;
import dev.axonia.health.api.FeedbackAverages;
import dev.axonia.health.api.SeriesPulse;
import dev.axonia.health.api.spi.EventsSignals;
import lombok.RequiredArgsConstructor;

/**
 * Events' implementation of the community-health signal port. Read-only by construction; windows are [from, to).
 * Callers guarantee to <= now, so "scheduled, not cancelled, in window" means "held in window".
 */
@Component
@RequiredArgsConstructor
class EventsSignalsAdapter implements EventsSignals {

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationContext organizationContext;

    private static final String HELD_TALKS = """
            FROM event_slot_presenter sp
            JOIN event_slot sl ON sp.slot_id = sl.id
            JOIN event_section sec ON sl.section_id = sec.id
            JOIN community_session cs ON sec.session_id = cs.id
            WHERE cs.organization_id = :organizationId AND cs.community_id = :communityId
              AND cs.status NOT IN ('CANCELLED', 'SKIPPED') AND cs.scheduled_at >= :from AND cs.scheduled_at < :to
            """;

    private static final String HELD_FOR_FEEDBACK = """
            cs.organization_id = :organizationId AND cs.community_id = :communityId
              AND cs.status = 'HELD' AND cs.scheduled_at >= :from AND cs.scheduled_at < :to
            """;

    private static final String FEEDBACK_ANSWERS = """
            SELECT session_id, scale, enjoyed, learned, count(*) AS answers FROM (
                SELECT cs.id AS session_id, 10 AS scale, f.enjoyed, f.learned
                FROM community_session cs
                JOIN session_feedback f ON f.session_id = cs.id AND f.organization_id = cs.organization_id
                WHERE cs.closed_at <= :revealedBefore AND {held}
                UNION ALL
                SELECT cs.id, i.scale, a.enjoyed, a.learned
                FROM community_session cs
                JOIN session_feedback_import i ON i.session_id = cs.id AND i.organization_id = cs.organization_id
                JOIN session_feedback_import_answer a ON a.import_id = i.id AND a.organization_id = i.organization_id
                WHERE {held}
            ) answer
            GROUP BY session_id, scale, enjoyed, learned
            """.replace("{held}", HELD_FOR_FEEDBACK);

    private static final String SESSION_BUCKET = "least(floor(extract(epoch FROM (cs.scheduled_at - :from))"
            + " / :bucketSeconds), :lastBucket)::int";

    private static final String TOPIC_BUCKET = "least(floor(extract(epoch FROM (t.created_at - :from))"
            + " / :bucketSeconds), :lastBucket)::int";

    private static final String TOPICS = """
            FROM topic t
            WHERE t.organization_id = :organizationId AND t.community_id = :communityId
              AND t.created_at >= :from AND t.created_at < :to
            """;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> talkCounts(long communityId, Instant from, Instant to) {

        return countsPerUser("SELECT sp.user_id, count(*) AS acts " + HELD_TALKS + " GROUP BY sp.user_id",
                window(communityId, from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> topicProposals(long communityId, Instant from, Instant to) {

        return countsPerUser(
                "SELECT t.proposer_user_id AS user_id, count(*) AS acts " + TOPICS + " GROUP BY t.proposer_user_id",
                window(communityId, from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> nextSessionAt(long communityId, Instant now) {

        var sql = """
                SELECT min(scheduled_at) AS next FROM community_session
                WHERE organization_id = :organizationId AND community_id = :communityId
                  AND status NOT IN ('CANCELLED', 'SKIPPED') AND scheduled_at > :now
                """;
        return sessionAt(sql, communityId, now);
    }

    private Optional<Instant> sessionAt(String sql, long communityId, Instant now) {

        var params = new MapSqlParameterSource().addValue("organizationId", organizationContext.organizationId())
                .addValue("communityId", communityId).addValue("now", Timestamp.from(now));
        var at = jdbc.queryForObject(sql, params, (rs, rowNum) -> rs.getTimestamp(1));
        return Optional.ofNullable(at).map(Timestamp::toInstant);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> lastHeldSessionAt(long communityId, Instant now) {

        var sql = """
                SELECT max(scheduled_at) AS last FROM community_session
                WHERE organization_id = :organizationId AND community_id = :communityId
                  AND status NOT IN ('CANCELLED', 'SKIPPED') AND scheduled_at < :now
                """;
        return sessionAt(sql, communityId, now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BucketTalks> talkCreditsPerBucket(long communityId, Instant from, Instant to, int buckets) {

        var sql = "SELECT " + SESSION_BUCKET + " AS bucket, sp.user_id, count(*) AS talks " + HELD_TALKS
                + " GROUP BY bucket, sp.user_id";
        return jdbc.query(sql, bucketParameters(communityId, from, to, buckets),
                (rs, rowNum) -> new BucketTalks(rs.getInt("bucket"), rs.getString("user_id"), rs.getLong("talks")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BucketCredit> topicCreditsPerBucket(long communityId, Instant from, Instant to, int buckets) {

        var sql = "SELECT DISTINCT " + TOPIC_BUCKET + " AS bucket, t.proposer_user_id AS user_id " + TOPICS;
        return credits(sql, bucketParameters(communityId, from, to, buckets));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BucketCredit> engagementCreditsPerBucket(long communityId, Instant from, Instant to, int buckets) {

        var sql = """
                SELECT DISTINCT bucket, user_id FROM (
                    SELECT v.user_id,
                           least(floor(extract(epoch FROM (v.created_at - :from)) / :bucketSeconds),
                                 :lastBucket)::int AS bucket
                    FROM topic_vote v
                    JOIN topic t ON v.topic_id = t.id
                    WHERE v.organization_id = :organizationId AND t.community_id = :communityId
                      AND v.created_at >= :from AND v.created_at < :to
                    UNION ALL
                    SELECT a.user_id,
                           least(floor(extract(epoch FROM (a.created_at - :from)) / :bucketSeconds),
                                 :lastBucket)::int
                    FROM session_attendance a
                    JOIN community_session cs ON a.session_id = cs.id
                    WHERE a.organization_id = :organizationId AND cs.community_id = :communityId
                      AND a.created_at >= :from AND a.created_at < :to
                ) act
                """;
        return credits(sql, bucketParameters(communityId, from, to, buckets));
    }

    private List<BucketCredit> credits(String sql, Map<String, Object> params) {

        return jdbc.query(sql, params, (rs, rowNum) -> new BucketCredit(rs.getInt("bucket"), rs.getString("user_id")));
    }

    private Map<String, Long> countsPerUser(String sql, MapSqlParameterSource params) {

        var counts = new HashMap<String, Long>();
        jdbc.query(sql, params, rs -> {
            counts.put(rs.getString("user_id"), rs.getLong("acts"));
        });
        return counts;
    }

    private Map<String, Object> bucketParameters(long communityId, Instant from, Instant to, int buckets) {

        return BucketedSeries.Read.builder().organizationId(organizationContext.organizationId())
                .communityId(communityId).from(from).to(to).buckets(buckets).build().query().parameters();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Instant> heldSessionDates(long communityId, Instant from, Instant to) {

        var sql = """
                SELECT scheduled_at FROM community_session
                WHERE organization_id = :organizationId AND community_id = :communityId
                  AND status NOT IN ('CANCELLED', 'SKIPPED') AND scheduled_at >= :from AND scheduled_at < :to
                ORDER BY scheduled_at
                """;
        return jdbc.query(sql, window(communityId, from, to),
                (rs, rowNum) -> rs.getTimestamp("scheduled_at").toInstant());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeriesPulse> activeSeries(long communityId) {

        var sql = """
                SELECT cadence_count, cadence_unit FROM event_series
                WHERE organization_id = :organizationId AND community_id = :communityId AND status = 'ACTIVE'
                """;
        var params = new MapSqlParameterSource().addValue("organizationId", organizationContext.organizationId())
                .addValue("communityId", communityId);
        return jdbc.query(sql, params, (rs, rowNum) -> new SeriesPulse(
                new Cadence(rs.getInt("cadence_count"), CadenceUnit.valueOf(rs.getString("cadence_unit"))).toDays()));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> engagedUserIds(long communityId, Instant from, Instant to) {

        var sql = """
                SELECT DISTINCT user_id FROM (
                    SELECT v.user_id
                    FROM topic_vote v
                    JOIN topic t ON v.topic_id = t.id
                    WHERE v.organization_id = :organizationId AND t.community_id = :communityId
                      AND v.created_at >= :from AND v.created_at < :to
                    UNION
                    SELECT a.user_id
                    FROM session_attendance a
                    JOIN community_session cs ON a.session_id = cs.id
                    WHERE a.organization_id = :organizationId AND cs.community_id = :communityId
                      AND a.created_at >= :from AND a.created_at < :to
                ) engaged
                """;
        return new HashSet<>(jdbc.query(sql, window(communityId, from, to), (rs, rowNum) -> rs.getString("user_id")));
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackAverages feedbackAverages(long communityId, Instant from, Instant to, Instant now) {

        var params = window(communityId, from, to).addValue("revealedBefore",
                Timestamp.from(now.minus(FeedbackRules.WINDOW)));
        var perSession = new HashMap<Long, SessionAnswers>();
        jdbc.query(FEEDBACK_ANSWERS, params, rs -> {
            perSession.merge(rs.getLong("session_id"), SessionAnswers.of(rs), SessionAnswers::plus);
        });
        var revealed = SessionAnswers.NONE;
        var sessions = 0;
        var sessionsTooFew = 0;
        for (var session : perSession.values()) {
            if (session.answers() >= FeedbackRules.MIN_REVEAL) {
                revealed = revealed.plus(session);
                sessions++;
            } else {
                sessionsTooFew++;
            }
        }
        return new FeedbackAverages(revealed.answers(), revealed.enjoyedAnswers(), revealed.enjoyedSum(),
                revealed.learnedAnswers(), revealed.learnedSum(), sessions, sessionsTooFew);
    }

    /** One session's answers on the ten-point range; each question counts only the answers that hold it. */
    private record SessionAnswers(long answers, long enjoyedAnswers, double enjoyedSum, long learnedAnswers,
            double learnedSum) {

        static final SessionAnswers NONE = new SessionAnswers(0, 0, 0, 0, 0);

        static SessionAnswers of(ResultSet rs) throws SQLException {

            var answers = rs.getLong("answers");
            var scale = rs.getInt("scale");
            var enjoyed = rs.getObject("enjoyed", Integer.class);
            var learned = rs.getObject("learned", Integer.class);
            return new SessionAnswers(answers, enjoyed == null ? 0 : answers, sumOf(enjoyed, scale, answers),
                    learned == null ? 0 : answers, sumOf(learned, scale, answers));
        }

        private static double sumOf(Integer value, int scale, long answers) {

            return value == null ? 0 : FeedbackRules.toTen(value, scale) * answers;
        }

        SessionAnswers plus(SessionAnswers other) {

            return new SessionAnswers(answers + other.answers, enjoyedAnswers + other.enjoyedAnswers,
                    enjoyedSum + other.enjoyedSum, learnedAnswers + other.learnedAnswers,
                    learnedSum + other.learnedSum);
        }
    }

    private MapSqlParameterSource window(long communityId, Instant from, Instant to) {

        return new MapSqlParameterSource().addValue("organizationId", organizationContext.organizationId())
                .addValue("communityId", communityId).addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));
    }
}
