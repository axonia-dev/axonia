package dev.axonia.events.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.Cadence;
import dev.axonia.events.CadenceUnit;
import dev.axonia.events.OrganizationContext;
import dev.axonia.health.BucketedSeries;
import dev.axonia.health.EventsSignals;
import dev.axonia.health.SeriesPulse;
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

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> contributionCounts(long communityId, Instant from, Instant to) {

        var sql = """
                SELECT user_id, count(*) AS acts FROM (
                    SELECT sp.user_id
                    FROM event_slot_presenter sp
                    JOIN event_slot sl ON sp.slot_id = sl.id
                    JOIN event_section sec ON sl.section_id = sec.id
                    JOIN community_session cs ON sec.session_id = cs.id
                    WHERE cs.organization_id = :organizationId AND cs.community_id = :communityId
                      AND cs.status <> 'CANCELLED' AND cs.scheduled_at >= :from AND cs.scheduled_at < :to
                    UNION ALL
                    SELECT t.proposer_user_id
                    FROM topic t
                    WHERE t.organization_id = :organizationId AND t.community_id = :communityId
                      AND t.created_at >= :from AND t.created_at < :to
                ) act GROUP BY user_id
                """;
        var counts = new HashMap<String, Long>();
        jdbc.query(sql, window(communityId, from, to), rs -> {
            counts.put(rs.getString("user_id"), rs.getLong("acts"));
        });
        return counts;
    }

    @Override
    public List<Long> distinctContributorsPerBucket(long communityId, Instant from, Instant to, int buckets) {

        // The inner UNION ALL is the one contributionCounts reads, so the series and the scalar can never disagree
        // about what a contribution is. Each arm buckets on its own act timestamp: the presenter's session schedule,
        // the topic's creation.
        var sql = """
                SELECT bucket, count(DISTINCT user_id) AS n FROM (
                    SELECT sp.user_id,
                           least(floor(extract(epoch FROM (cs.scheduled_at - :from)) / :bucketSeconds),
                                 :lastBucket)::int AS bucket
                    FROM event_slot_presenter sp
                    JOIN event_slot sl ON sp.slot_id = sl.id
                    JOIN event_section sec ON sl.section_id = sec.id
                    JOIN community_session cs ON sec.session_id = cs.id
                    WHERE cs.organization_id = :organizationId AND cs.community_id = :communityId
                      AND cs.status <> 'CANCELLED' AND cs.scheduled_at >= :from AND cs.scheduled_at < :to
                    UNION ALL
                    SELECT t.proposer_user_id,
                           least(floor(extract(epoch FROM (t.created_at - :from)) / :bucketSeconds),
                                 :lastBucket)::int
                    FROM topic t
                    WHERE t.organization_id = :organizationId AND t.community_id = :communityId
                      AND t.created_at >= :from AND t.created_at < :to
                ) act GROUP BY bucket
                """;
        var read = BucketedSeries.Read.builder().organizationId(organizationContext.organizationId())
                .communityId(communityId).from(from).to(to).buckets(buckets).build();
        return BucketedSeries.of(jdbc, sql, read);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Instant> heldSessionDates(long communityId, Instant from, Instant to) {

        var sql = """
                SELECT scheduled_at FROM community_session
                WHERE organization_id = :organizationId AND community_id = :communityId
                  AND status <> 'CANCELLED' AND scheduled_at >= :from AND scheduled_at < :to
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
    public List<Long> engagedMembersPerBucket(long communityId, Instant from, Instant to, int buckets) {

        // The two engagement act sources are the ones engagedUserIds already reads. Each arm buckets on its own act
        // timestamp, the vote's and the attendance's, never on the parent topic's or session's. engagedUserIds can
        // deduplicate with a plain UNION; here the outer count(DISTINCT user_id) already does, so UNION ALL is both
        // correct and cheaper.
        var sql = """
                SELECT bucket, count(DISTINCT user_id) AS n FROM (
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
                ) act GROUP BY bucket
                """;
        var read = BucketedSeries.Read.builder().organizationId(organizationContext.organizationId())
                .communityId(communityId).from(from).to(to).buckets(buckets).build();
        return BucketedSeries.of(jdbc, sql, read);
    }

    private MapSqlParameterSource window(long communityId, Instant from, Instant to) {

        return new MapSqlParameterSource().addValue("organizationId", organizationContext.organizationId())
                .addValue("communityId", communityId).addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));
    }
}
