package dev.axonia.bookmarks.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.bookmarks.OrganizationContext;
import dev.axonia.health.BucketedSeries;
import dev.axonia.health.ReuseCounts;
import dev.axonia.health.ReuseSignals;
import lombok.RequiredArgsConstructor;

/**
 * Bookmarks' implementation of the reuse health signal. Single-table aggregate on {@code bookmark}; self-saves
 * excluded.
 */
@Component
@RequiredArgsConstructor
class ReuseSignalsAdapter implements ReuseSignals {

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationContext organizationContext;

    @Override
    @Transactional(readOnly = true)
    public ReuseCounts reuseCounts(long communityId, Instant from, Instant to) {

        var sql = """
                SELECT count(*) AS saves,
                       count(DISTINCT b.saveable_id) AS distinct_artifacts,
                       count(*) FILTER (WHERE b.helped IS TRUE)  AS helped,
                       count(*) FILTER (WHERE b.helped IS FALSE) AS not_helped
                FROM bookmark b
                WHERE b.organization_id = :organizationId AND b.community_id = :communityId
                  AND b.saveable_type = 'ARTIFACT'
                  AND b.created_at >= :from AND b.created_at < :to
                  AND b.member_user_id <> b.source_author_user_id
                """;
        var params = new MapSqlParameterSource().addValue("organizationId", organizationContext.organizationId())
                .addValue("communityId", communityId).addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));
        return jdbc.queryForObject(sql, params, (rs, n) -> new ReuseCounts(rs.getLong("saves"),
                rs.getLong("distinct_artifacts"), rs.getLong("helped"), rs.getLong("not_helped")));
    }

    @Override
    public List<Long> savesPerBucket(long communityId, Instant from, Instant to, int buckets) {

        var sql = """
                SELECT least(floor(extract(epoch FROM (b.created_at - :from)) / :bucketSeconds), :lastBucket)::int
                         AS bucket,
                       count(*) AS n
                FROM bookmark b
                WHERE b.organization_id = :organizationId AND b.community_id = :communityId
                  AND b.saveable_type = 'ARTIFACT'
                  AND b.created_at >= :from AND b.created_at < :to
                  AND b.member_user_id <> b.source_author_user_id
                GROUP BY 1
                """;
        var read = BucketedSeries.Read.builder().organizationId(organizationContext.organizationId())
                .communityId(communityId).from(from).to(to).buckets(buckets).build();
        return BucketedSeries.of(jdbc, sql, read);
    }
}
