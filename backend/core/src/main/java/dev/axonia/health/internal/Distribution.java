package dev.axonia.health.internal;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The anonymous contribution distribution. Identity is dropped at construction — counts in, sorted shares out — which
 * makes the "aggregates only, never names" stance structural: nothing downstream of this type can name anyone.
 */
public final class Distribution {

    private final List<Double> shares;

    private Distribution(List<Double> shares) {

        this.shares = shares;
    }

    /** Builds the distribution from per-member contribution counts (values only). */
    public static Distribution of(Collection<Long> contributionCounts) {

        var total = contributionCounts.stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            return new Distribution(List.of());
        }
        var shares = contributionCounts.stream().filter(count -> count > 0).sorted(Comparator.reverseOrder())
                .map(count -> (double) count / total).toList();
        return new Distribution(shares);
    }

    /** Smallest number of contributors whose cumulative share first reaches >= 50%. 0 = no activity. */
    public int busFactor() {

        var cumulative = 0.0;
        for (var i = 0; i < shares.size(); i++) {
            cumulative += shares.get(i);
            if (cumulative >= 0.5 - 1e-9) {
                return i + 1;
            }
        }
        return shares.size();
    }

    /** Share carried by the top three contributors. */
    public double top3Share() {

        return shares.stream().limit(3).mapToDouble(Double::doubleValue).sum();
    }

    /** Sorted high-to-low, sums to 1.0 (or empty when there was no activity). */
    public List<Double> shares() {

        return List.copyOf(shares);
    }
}
