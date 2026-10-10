package dev.axonia.shared.api.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Builder;

/**
 * The bind values and the result shape of one grouped bucket-count query: a query that selects a {@code bucket} index
 * and its count {@code n}, grouped by bucket, over one community's window. The persistence half of a bucketed series,
 * shared by every adapter that answers one in a single query.
 */
@Builder
public record BucketCountQuery(String organizationId, long communityId, Instant from, Instant to, double bucketSeconds,
        int lastBucket) {

    /**
     * The standard {@code organizationId}, {@code communityId}, {@code from}, {@code to}, {@code bucketSeconds} and
     * {@code lastBucket} parameters.
     */
    public Map<String, Object> parameters() {

        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("organizationId", organizationId);
        parameters.put("communityId", communityId);
        parameters.put("from", Timestamp.from(from));
        parameters.put("to", Timestamp.from(to));
        parameters.put("bucketSeconds", bucketSeconds);
        parameters.put("lastBucket", lastBucket);
        return parameters;
    }

    /** The sparse result, one row per non-empty bucket, as counts by bucket index. */
    public static Map<Integer, Long> counts(List<Map<String, Object>> rows) {

        var counts = new HashMap<Integer, Long>();
        rows.forEach(row -> counts.put(((Number) row.get("bucket")).intValue(), ((Number) row.get("n")).longValue()));
        return counts;
    }
}
