package dev.axonia.health.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.axonia.shared.api.persistence.BucketCountQuery;

import static org.assertj.core.api.Assertions.assertThat;

class BucketedSeriesTest {

    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = FROM.plus(Duration.ofDays(12));

    private final BucketedSeries.Read read = BucketedSeries.Read.builder().organizationId("acme").communityId(7L)
            .from(FROM).to(TO).buckets(12).build();

    @Test
    void query_carriesTheTenantTheCommunityTheWindowAndTheBucketing() {

        assertThat(read.query()).isEqualTo(BucketCountQuery.builder().organizationId("acme").communityId(7L).from(FROM)
                .to(TO).bucketSeconds(86400d).lastBucket(11).build());
    }

    @Test
    void series_zeroFillsTheSparseCountsToTheRequestedLength() {

        assertThat(read.series(Map.of(0, 3L, 11, 5L))).containsExactly(3L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 5L);
    }
}
