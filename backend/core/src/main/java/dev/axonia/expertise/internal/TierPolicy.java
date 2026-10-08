package dev.axonia.expertise.internal;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import dev.axonia.expertise.CommunityTier;
import dev.axonia.expertise.Evidence;
import dev.axonia.expertise.EvidenceItem;
import dev.axonia.expertise.ExpertiseSignal;
import dev.axonia.expertise.SignalType;
import dev.axonia.expertise.Tier;

public final class TierPolicy {

    private TierPolicy() {
    }

    /**
     * Derives the highest tier from the given signals.
     * <ul>
     * <li>Any PRESENTED signal → PRACTITIONER</li>
     * <li>Any CONTRIBUTED signal → CONTRIBUTOR</li>
     * <li>Otherwise → INTERESTED (including empty collection)</li>
     * </ul>
     */
    public static Tier tierOf(Collection<ExpertiseSignal> signals) {

        var hasPresented = false;
        var hasContributed = false;

        for (var signal : signals) {
            if (signal.signalType() == SignalType.PRESENTED) {
                hasPresented = true;
            } else if (signal.signalType() == SignalType.CONTRIBUTED) {
                hasContributed = true;
            }
        }

        if (hasPresented) {
            return Tier.PRACTITIONER;
        }
        if (hasContributed) {
            return Tier.CONTRIBUTOR;
        }
        return Tier.INTERESTED;
    }

    /**
     * All signals as EvidenceItem, newest-first, tie-broken by source for a stable page boundary. Titles unresolved.
     */
    public static List<EvidenceItem> orderedItems(Collection<ExpertiseSignal> signals) {

        return signals.stream()
                .sorted(Comparator.comparing(ExpertiseSignal::occurredAt).reversed()
                        .thenComparing(s -> s.sourceType().name()).thenComparing(ExpertiseSignal::sourceId))
                .map(s -> EvidenceItem.builder().signalType(s.signalType()).sourceType(s.sourceType())
                        .sourceId(s.sourceId()).label("").communitySlug(null).occurredAt(s.occurredAt()).build())
                .toList();
    }

    /**
     * Derives counts per signal type and the most recent signals as EvidenceItem, sorted newest-first and capped at
     * recentLimit.
     */
    public static Evidence evidenceOf(Collection<ExpertiseSignal> signals, int recentLimit) {

        var presentedCount = 0;
        var contributedCount = 0;
        var offeredCount = 0;

        for (var signal : signals) {
            var type = signal.signalType();
            if (type == SignalType.PRESENTED) {
                presentedCount++;
            } else if (type == SignalType.CONTRIBUTED) {
                contributedCount++;
            } else if (type == SignalType.OFFERED) {
                offeredCount++;
            }
            // A COMPLETED signal (a closed action item) still surfaces in `recent` below and on
            // My Impact, but is deliberately not given its own summary bucket here, nor does it elevate the tier in
            // tierOf: how completed operational work should weigh in the expertise tier is a product decision left
            // open.
        }

        var recent = orderedItems(signals).stream().limit(recentLimit).toList();

        return Evidence.builder().presentedCount(presentedCount).contributedCount(contributedCount)
                .offeredCount(offeredCount).recent(recent).build();
    }

    /** One tier per community the signals came from, each rolled up from that community's signals alone. */
    public static List<CommunityTier> tiersByCommunity(Collection<ExpertiseSignal> signals) {

        return signals.stream().filter(s -> Objects.nonNull(s.communityId()))
                .collect(Collectors.groupingBy(ExpertiseSignal::communityId, TreeMap::new, Collectors.toList()))
                .entrySet().stream().map(e -> new CommunityTier(e.getKey(), tierOf(e.getValue()))).toList();
    }

    /**
     * Numeric rank for ordering: PRACTITIONER=0, CONTRIBUTOR=1, INTERESTED=2, OPEN_TO_QUESTIONS=3.
     */
    public static int rank(Tier tier) {

        return switch (tier) {
            case PRACTITIONER -> 0;
            case CONTRIBUTOR -> 1;
            case INTERESTED -> 2;
            case OPEN_TO_QUESTIONS -> 3;
        };
    }
}
