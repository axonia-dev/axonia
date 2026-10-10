package dev.axonia.health.api;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BucketsTest {

    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");

    @Test
    void count_isTwelve() {

        assertThat(Buckets.COUNT).isEqualTo(12);
    }

    @Test
    void secondsPerBucket_splitsTheWindowEvenly() {

        var to = FROM.plus(Duration.ofDays(90));

        assertThat(Buckets.secondsPerBucket(FROM, to, Buckets.COUNT)).isEqualTo(90 * 86400d / 12);
    }

    @Test
    void secondsPerBucket_keepsTheRemainderOfAnUnevenWindow() {

        // 30 days over 12 buckets is 2.5 days, which integer division would silently
        // truncate to 2 and push every later event a bucket to the left.
        var to = FROM.plus(Duration.ofDays(30));

        assertThat(Buckets.secondsPerBucket(FROM, to, Buckets.COUNT)).isEqualTo(2.5 * 86400);
    }

    @Test
    void secondsPerBucket_dividesByTheCountItIsGiven() {

        // The count is the whole point of the signature: a caller asking for six buckets
        // must get six sixths of the window, not twelve twelfths of it six times over.
        var to = FROM.plus(Duration.ofDays(90));

        assertThat(Buckets.secondsPerBucket(FROM, to, 6)).isEqualTo(15 * 86400d);
        assertThat(Buckets.secondsPerBucket(FROM, to, 1)).isEqualTo(90 * 86400d);
    }

    @Test
    void zeroFill_producesADenseSeriesFromASparseMap() {

        Map<Integer, Long> sparse = new LinkedHashMap<>();
        sparse.put(0, 3L);
        sparse.put(11, 7L);

        assertThat(Buckets.zeroFill(sparse, 12)).containsExactly(3L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 7L);
    }

    @Test
    void zeroFill_ofNothingIsTwelveZeroes() {

        assertThat(Buckets.zeroFill(Map.of(), 12)).hasSize(12).allMatch(count -> count == 0L);
    }

    @Test
    void zeroFill_dropsAnIndexOutsideTheSeries() {

        // A row landing exactly on `to` would compute index 12. The adapters clamp, but
        // zeroFill must not blow up if one ever slips through.
        assertThat(Buckets.zeroFill(Map.of(12, 5L), 12)).hasSize(12).allMatch(count -> count == 0L);
    }

    @Test
    void zeroFill_honoursANonDefaultBucketCount() {

        assertThat(Buckets.zeroFill(Map.of(0, 4L, 4, 9L), 5)).containsExactly(4L, 0L, 0L, 0L, 9L);
    }
}
