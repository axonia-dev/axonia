package dev.axonia.knowledge.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.health.BucketedSeries;
import dev.axonia.health.OpeningCounts;
import dev.axonia.health.OpeningSignals;
import dev.axonia.knowledge.OrganizationContext;
import lombok.RequiredArgsConstructor;

/**
 * Knowledge's implementation of the artifact-opening health signal.
 *
 * <p>
 * The join to {@code artifact} is what excludes a contributor's own opens. Bookmarks has to denormalise the author onto
 * every row to do the same thing, because it cannot reach {@code artifact} from another bounded context; knowledge owns
 * both tables, so it joins instead and keeps one less identifier on the row.
 *
 * <p>
 * Counted regardless of the artifact's archival status, exactly as {@link ArtifactSignalsAdapter} counts captures:
 * somebody opening a retired document from a stale tab genuinely used it.
 */
@Component
@RequiredArgsConstructor
class OpeningSignalsAdapter implements OpeningSignals {

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationContext organizationContext;

    @Override
    @Transactional(readOnly = true)
    public OpeningCounts openingCounts(long communityId, Instant from, Instant to) {

        var sql = """
                SELECT count(*) AS openings, count(DISTINCT o.artifact_id) AS distinct_artifacts
                FROM artifact_opening o JOIN artifact a ON a.id = o.artifact_id
                WHERE o.organization_id = :organizationId AND o.community_id = :communityId
                  AND o.created_at >= :from AND o.created_at < :to
                  AND o.member_user_id <> a.contributor_user_id
                """;
        var params = new MapSqlParameterSource().addValue("organizationId", organizationContext.organizationId())
                .addValue("communityId", communityId).addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));
        return jdbc.queryForObject(sql, params,
                (rs, n) -> new OpeningCounts(rs.getLong("openings"), rs.getLong("distinct_artifacts")));
    }

    @Override
    public List<Long> openingsPerBucket(long communityId, Instant from, Instant to, int buckets) {

        var sql = """
                SELECT least(floor(extract(epoch FROM (o.created_at - :from)) / :bucketSeconds), :lastBucket)::int
                         AS bucket,
                       count(*) AS n
                FROM artifact_opening o JOIN artifact a ON a.id = o.artifact_id
                WHERE o.organization_id = :organizationId AND o.community_id = :communityId
                  AND o.created_at >= :from AND o.created_at < :to
                  AND o.member_user_id <> a.contributor_user_id
                GROUP BY 1
                """;
        var read = BucketedSeries.Read.builder().organizationId(organizationContext.organizationId())
                .communityId(communityId).from(from).to(to).buckets(buckets).build();
        return BucketedSeries.of(jdbc, sql, read);
    }
}
