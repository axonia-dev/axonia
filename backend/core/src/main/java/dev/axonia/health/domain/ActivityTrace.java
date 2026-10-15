package dev.axonia.health.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import dev.axonia.health.api.Buckets;

/**
 * A community's raw signs of life: how many sessions were held, artifacts added to the Library and members joined on
 * each of the last {@code days} days, oldest first. Counts only, nothing that names anyone, and no reading of them:
 * this is not the health snapshot and is not gated like it.
 */
public record ActivityTrace(int days, List<Long> sessionsHeld, List<Long> libraryAdditions, List<Long> joins) {

    public static final int DAYS = 90;

    /** Per-day counts of {@code dates} over [from, from + days), oldest first, zero-filled. */
    public static List<Long> daysOf(List<Instant> dates, Instant from, int days) {

        var to = from.plus(Duration.ofDays(days));
        var counts = new HashMap<Integer, Long>();
        dates.stream().filter(date -> !date.isBefore(from) && date.isBefore(to))
                .map(date -> (int) Duration.between(from, date).toDays())
                .forEach(day -> counts.merge(day, 1L, Long::sum));
        return Buckets.zeroFill(counts, days);
    }
}
