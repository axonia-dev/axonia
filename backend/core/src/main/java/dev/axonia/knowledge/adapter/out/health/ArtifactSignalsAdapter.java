package dev.axonia.knowledge.adapter.out.health;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.health.api.BucketCredit;
import dev.axonia.health.api.BucketedSeries;
import dev.axonia.health.api.spi.ArtifactSignals;
import dev.axonia.knowledge.application.port.out.OrganizationContext;
import dev.axonia.shared.api.persistence.BucketCountQuery;
import lombok.RequiredArgsConstructor;

/**
 * Knowledge's implementation of the artifact-capture health signal. Counts capture acts regardless of later archival.
 */
@Component
@RequiredArgsConstructor
class ArtifactSignalsAdapter implements ArtifactSignals {

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationContext organizationContext;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> artifactCounts(long communityId, Instant from, Instant to) {

        var sql = """
                SELECT contributor_user_id, count(*) AS acts FROM artifact
                WHERE organization_id = :organizationId AND community_id = :communityId
                  AND created_at >= :from AND created_at < :to
                GROUP BY contributor_user_id
                """;
        var params = new MapSqlParameterSource().addValue("organizationId", organizationContext.organizationId())
                .addValue("communityId", communityId).addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));
        var counts = new HashMap<String, Long>();
        jdbc.query(sql, params, rs -> {
            counts.put(rs.getString("contributor_user_id"), rs.getLong("acts"));
        });
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> artifactsPerBucket(long communityId, Instant from, Instant to, int buckets) {

        var sql = """
                SELECT least(floor(extract(epoch FROM (created_at - :from)) / :bucketSeconds), :lastBucket)::int
                         AS bucket,
                       count(*) AS n
                FROM artifact
                WHERE organization_id = :organizationId AND community_id = :communityId AND status = 'ACTIVE'
                  AND created_at >= :from AND created_at < :to
                GROUP BY 1
                """;
        var read = BucketedSeries.Read.builder().organizationId(organizationContext.organizationId())
                .communityId(communityId).from(from).to(to).buckets(buckets).build();
        return read.series(BucketCountQuery.counts(jdbc.queryForList(sql, read.query().parameters())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BucketCredit> artifactCreditsPerBucket(long communityId, Instant from, Instant to, int buckets) {

        var sql = """
                SELECT DISTINCT
                       least(floor(extract(epoch FROM (created_at - :from)) / :bucketSeconds), :lastBucket)::int
                         AS bucket,
                       contributor_user_id
                FROM artifact
                WHERE organization_id = :organizationId AND community_id = :communityId
                  AND created_at >= :from AND created_at < :to
                """;
        var read = BucketedSeries.Read.builder().organizationId(organizationContext.organizationId())
                .communityId(communityId).from(from).to(to).buckets(buckets).build();
        return jdbc.query(sql, read.query().parameters(),
                (rs, rowNum) -> new BucketCredit(rs.getInt("bucket"), rs.getString("contributor_user_id")));
    }
}
