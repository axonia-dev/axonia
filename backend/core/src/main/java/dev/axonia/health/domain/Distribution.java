package dev.axonia.health.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Who stood on the stage: the anonymous talks distribution. Identity is dropped at construction - counts in, sorted
 * shares out - which makes the "aggregates only, never names" stance structural: nothing downstream of this type can
 * name anyone.
 */
public final class Distribution {

    private final long talks;
    private final List<Double> shares;

    private Distribution(long talks, List<Double> shares) {

        this.talks = talks;
        this.shares = shares;
    }

    /** Builds the distribution from per-speaker talk counts (values only). */
    public static Distribution of(Collection<Long> talkCounts) {

        var total = talkCounts.stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            return new Distribution(0, List.of());
        }
        var shares = talkCounts.stream().filter(count -> count > 0).sorted(Comparator.reverseOrder())
                .map(count -> (double) count / total).toList();
        return new Distribution(total, shares);
    }

    public long talks() {

        return talks;
    }

    public int speakers() {

        return shares.size();
    }

    /** Smallest number of speakers whose talks reach at least half of all talks. 0 = no talks. */
    public int speakersForHalf() {

        var cumulative = 0.0;
        for (var i = 0; i < shares.size(); i++) {
            cumulative += shares.get(i);
            if (cumulative >= 0.5 - 1e-9) {
                return i + 1;
            }
        }
        return shares.size();
    }

    /** Share of the talks given by the top speaker, 0.0 when there were none. */
    public double topShare() {

        return shares.isEmpty() ? 0.0 : shares.getFirst();
    }

    /** Sorted high-to-low, sums to 1.0 (or empty when there were no talks). */
    public List<Double> shares() {

        return List.copyOf(shares);
    }
}
