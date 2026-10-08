package dev.axonia.health;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.Builder;

/**
 * Runs one grouped, zero-filled bucket-count query. Every read-only signal port that answers a whole series in a single
 * query binds the same five values - tenant, community, window and bucket count - and reduces the same shape of result
 * set, a sparse {@code bucket -> n} map, to a dense series. That shared tail lives here, beside {@link Buckets} rather
 * than inside any one adapter, precisely because five modules across five bounded contexts each implement one such port
 * and none of them owns the others.
 */
public final class BucketedSeries {

    /**
     * What a bucketed query binds: the tenant, the community, the window it covers, and the number of buckets that
     * window is cut into. The count drives both the width of a bucket and the index of the last one, so a series is
     * only ever as long, and as coarse, as its caller asked for.
     */
    @Builder
    public record Read(String organizationId, long communityId, Instant from, Instant to, int buckets) {
    }

    private BucketedSeries() {
    }

    /**
     * Runs {@code sql} - which must select a {@code bucket} and an {@code n} column, grouped by bucket - binding the
     * standard {@code organizationId}, {@code communityId}, {@code from}, {@code to}, {@code bucketSeconds} and
     * {@code lastBucket} parameters from {@code read}, and zero-fills the result into a dense series.
     */
    public static List<Long> of(NamedParameterJdbcTemplate jdbc, String sql, Read read) {

        var params = new MapSqlParameterSource().addValue("organizationId", read.organizationId())
                .addValue("communityId", read.communityId()).addValue("from", Timestamp.from(read.from()))
                .addValue("to", Timestamp.from(read.to()))
                .addValue("bucketSeconds", Buckets.secondsPerBucket(read.from(), read.to(), read.buckets()))
                .addValue("lastBucket", read.buckets() - 1);
        var counts = new HashMap<Integer, Long>();
        RowCallbackHandler handler = rs -> counts.put(rs.getInt("bucket"), rs.getLong("n"));
        jdbc.query(sql, params, handler);
        return Buckets.zeroFill(counts, read.buckets());
    }
}
