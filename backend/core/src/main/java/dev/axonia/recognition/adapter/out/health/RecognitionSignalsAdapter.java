package dev.axonia.recognition.adapter.out.health;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.health.api.BucketedSeries;
import dev.axonia.health.api.spi.RecognitionSignals;
import dev.axonia.recognition.application.port.out.OrganizationContext;
import dev.axonia.shared.api.persistence.BucketCountQuery;
import lombok.RequiredArgsConstructor;

/**
 * Recognition's implementation of the community-health signal port. Returns the community aggregate only - per-lover
 * and per-recipient data never crosses this boundary; the private "how it landed" view stays its owner's.
 */
@Component
@RequiredArgsConstructor
class RecognitionSignalsAdapter implements RecognitionSignals {

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationContext organizationContext;

    @Override
    @Transactional(readOnly = true)
    public long lovesGiven(long communityId, Instant from, Instant to) {

        var sql = """
                SELECT count(*) FROM contribution_love l
                JOIN recognized_contribution rc ON l.recognized_contribution_id = rc.id
                WHERE l.organization_id = :organizationId AND rc.community_id = :communityId
                  AND l.created_at >= :from AND l.created_at < :to
                """;
        var params = new MapSqlParameterSource().addValue("organizationId", organizationContext.organizationId())
                .addValue("communityId", communityId).addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));
        return Objects.requireNonNull(jdbc.queryForObject(sql, params, Long.class));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> lovesPerBucket(long communityId, Instant from, Instant to, int buckets) {

        var sql = """
                SELECT least(floor(extract(epoch FROM (l.created_at - :from)) / :bucketSeconds), :lastBucket)::int
                         AS bucket,
                       count(*) AS n
                FROM contribution_love l
                JOIN recognized_contribution rc ON l.recognized_contribution_id = rc.id
                WHERE l.organization_id = :organizationId AND rc.community_id = :communityId
                  AND l.created_at >= :from AND l.created_at < :to
                GROUP BY 1
                """;
        var read = BucketedSeries.Read.builder().organizationId(organizationContext.organizationId())
                .communityId(communityId).from(from).to(to).buckets(buckets).build();
        return read.series(BucketCountQuery.counts(jdbc.queryForList(sql, read.query().parameters())));
    }
}
