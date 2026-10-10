package dev.axonia.shared.api.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BucketCountQueryTest {

    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-03-13T00:00:00Z");

    @Test
    void parametersBindTheTenantTheCommunityTheWindowAndTheBucketing() {

        var query = BucketCountQuery.builder().organizationId("acme").communityId(7L).from(FROM).to(TO)
                .bucketSeconds(86400d).lastBucket(11).build();

        assertThat(query.parameters()).containsExactly(Map.entry("organizationId", "acme"),
                Map.entry("communityId", 7L), Map.entry("from", Timestamp.from(FROM)),
                Map.entry("to", Timestamp.from(TO)), Map.entry("bucketSeconds", 86400d), Map.entry("lastBucket", 11));
    }

    @Test
    void countsReadEachRowsBucketAndCount() {

        var rows = List.<Map<String, Object>>of(Map.of("bucket", 0, "n", 3L), Map.of("bucket", 11, "n", 5));

        assertThat(BucketCountQuery.counts(rows)).containsOnly(Map.entry(0, 3L), Map.entry(11, 5L));
    }
}
