package dev.axonia.health.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.axonia.health.api.SeriesPulse;

/**
 * Pure cadence read from the sessions actually held. The expected gap is the shortest live series cadence, otherwise
 * the held sessions' own median gap; a community running one-off sessions is read by its own rhythm, never as dormant.
 */
public final class CadenceClassifier {

    /** A gap this much over the expected cadence still counts as on-rhythm. */
    private static final double TOLERANCE = 1.5;

    /** With no expected gap, "recently held" or "soon scheduled" means within this many days. */
    private static final int AD_HOC_RECENCY_DAYS = 45;

    private CadenceClassifier() {
    }

    public record CadenceRead(CadenceStatus status, int sessionsHeld, Integer medianGapDays) {
    }

    public static CadenceRead classify(List<Instant> heldDates, List<SeriesPulse> activeSeries, Instant nextSessionAt,
            Instant now) {

        var sorted = heldDates.stream().sorted().toList();
        var medianGap = medianGapDays(occasions(sorted));
        if (sorted.isEmpty()) {
            var waiting = !activeSeries.isEmpty() || nextSessionAt != null;
            return new CadenceRead(waiting ? CadenceStatus.STALLED : CadenceStatus.DORMANT, 0, null);
        }
        var steady = expectedGap(activeSeries, medianGap)
                .map(expected -> withinRhythm(sorted, medianGap, expected, now))
                .orElseGet(() -> recentOrSoon(sorted, nextSessionAt, now));
        return new CadenceRead(steady ? CadenceStatus.STEADY : CadenceStatus.SLOWING, sorted.size(), medianGap);
    }

    private static Optional<Integer> expectedGap(List<SeriesPulse> activeSeries, Integer medianGap) {

        var seriesCadence = activeSeries.stream().map(SeriesPulse::cadenceDays).filter(Objects::nonNull)
                .min(Integer::compareTo);
        return seriesCadence.isPresent() ? seriesCadence : Optional.ofNullable(medianGap);
    }

    private static boolean withinRhythm(List<Instant> sorted, Integer medianGap, int expected, Instant now) {

        var daysSinceLast = daysSinceLast(sorted, now);
        var allowed = expected * TOLERANCE;
        var rhythmGap = medianGap != null ? medianGap : daysSinceLast;
        return rhythmGap <= allowed && daysSinceLast <= allowed;
    }

    private static boolean recentOrSoon(List<Instant> sorted, Instant nextSessionAt, Instant now) {

        var soon = nextSessionAt != null && Duration.between(now, nextSessionAt).toDays() <= AD_HOC_RECENCY_DAYS;
        return soon || daysSinceLast(sorted, now) <= AD_HOC_RECENCY_DAYS;
    }

    private static long daysSinceLast(List<Instant> sorted, Instant now) {

        return Duration.between(sorted.getLast(), now).toDays();
    }

    /**
     * Sessions less than a day after the previous kept one are the same occasion (a double-header, a morning and an
     * afternoon), so a zero-day gap never becomes the expected rhythm.
     */
    private static List<Instant> occasions(List<Instant> sorted) {

        var occasions = new ArrayList<Instant>();
        for (var date : sorted) {
            if (occasions.isEmpty() || Duration.between(occasions.getLast(), date).toDays() >= 1) {
                occasions.add(date);
            }
        }
        return occasions;
    }

    /** Sub-day drift (a DST shift, a few minutes) must not cost a gap a whole day. */
    private static long roundedDays(Duration gap) {

        return Math.round(gap.toSeconds() / 86_400.0);
    }

    private static Integer medianGapDays(List<Instant> sorted) {

        if (sorted.size() < 2) {
            return null;
        }
        var gaps = new ArrayList<Long>();
        for (var i = 1; i < sorted.size(); i++) {
            gaps.add(roundedDays(Duration.between(sorted.get(i - 1), sorted.get(i))));
        }
        gaps.sort(Long::compareTo);
        var mid = gaps.size() / 2;
        var median = gaps.size() % 2 == 1 ? gaps.get(mid) : Math.round((gaps.get(mid - 1) + gaps.get(mid)) / 2.0);
        return (int) median;
    }
}
