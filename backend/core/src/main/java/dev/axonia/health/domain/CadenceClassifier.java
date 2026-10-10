package dev.axonia.health.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.axonia.health.api.SeriesPulse;

/** Pure cadence read: the held-session rhythm against the live series' cadence hints. */
public final class CadenceClassifier {

    /** A gap this much over the expected cadence still counts as on-rhythm. */
    private static final double TOLERANCE = 1.5;

    /** Ad-hoc series carry no expectation; "recently held" means within this many days. */
    private static final int AD_HOC_RECENCY_DAYS = 45;

    private CadenceClassifier() {
    }

    public record CadenceRead(CadenceStatus status, int sessionsHeld, Integer medianGapDays) {
    }

    public static CadenceRead classify(List<Instant> heldDates, List<SeriesPulse> activeSeries, Instant now) {

        var sorted = heldDates.stream().sorted().toList();
        var medianGap = medianGapDays(sorted);
        if (activeSeries.isEmpty()) {
            return new CadenceRead(CadenceStatus.DORMANT, sorted.size(), medianGap);
        }
        if (sorted.isEmpty()) {
            return new CadenceRead(CadenceStatus.STALLED, 0, null);
        }
        var status = onRhythm(sorted, activeSeries, medianGap, now) ? CadenceStatus.STEADY : CadenceStatus.SLOWING;
        return new CadenceRead(status, sorted.size(), medianGap);
    }

    private static boolean onRhythm(List<Instant> sorted, List<SeriesPulse> activeSeries, Integer medianGap,
            Instant now) {

        var daysSinceLast = Duration.between(sorted.getLast(), now).toDays();
        var expected = activeSeries.stream().map(SeriesPulse::cadenceDays).filter(Objects::nonNull)
                .min(Integer::compareTo);
        if (expected.isEmpty()) {
            return daysSinceLast <= AD_HOC_RECENCY_DAYS;
        }
        var allowed = expected.get() * TOLERANCE;
        var rhythmGap = medianGap != null ? medianGap : daysSinceLast;
        return rhythmGap <= allowed && daysSinceLast <= allowed;
    }

    private static Integer medianGapDays(List<Instant> sorted) {

        if (sorted.size() < 2) {
            return null;
        }
        var gaps = new ArrayList<Long>();
        for (var i = 1; i < sorted.size(); i++) {
            gaps.add(Duration.between(sorted.get(i - 1), sorted.get(i)).toDays());
        }
        gaps.sort(Long::compareTo);
        var mid = gaps.size() / 2;
        var median = gaps.size() % 2 == 1 ? gaps.get(mid) : Math.round((gaps.get(mid - 1) + gaps.get(mid)) / 2.0);
        return (int) median;
    }
}
