package dev.axonia.health;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bucket arithmetic for a snapshot's series. A window is split into {@code buckets} equal buckets whatever its length,
 * so 30 days over the default twelve buckets by 2.5 days and 180 days by 15.
 *
 * <p>
 * {@link #COUNT} is the count the snapshot asks for, not the count this arithmetic assumes: every method here takes the
 * caller's count, so a port handed some other number is bucketed over that number and not over twelve.
 *
 * <p>
 * Twelve is enough to show a direction on a phone-width sparkline and few enough that a 30-day window's buckets still
 * contain events.
 *
 * <p>
 * The seconds-per-bucket is deliberately a double: 30 days over 12 buckets is 2.5 days, and truncating that to 2 would
 * shift every later event one bucket to the left.
 */
public final class Buckets {

    public static final int COUNT = 12;

    private Buckets() {
    }

    public static double secondsPerBucket(Instant from, Instant to, int buckets) {

        return (double) Duration.between(from, to).toSeconds() / buckets;
    }

    /** Turns a sparse bucket-index to count map into a dense series of exactly {@code buckets} entries. */
    public static List<Long> zeroFill(Map<Integer, Long> countsByBucket, int buckets) {

        var series = new ArrayList<Long>(buckets);
        for (var index = 0; index < buckets; index++) {
            series.add(countsByBucket.getOrDefault(index, 0L));
        }
        return List.copyOf(series);
    }
}
