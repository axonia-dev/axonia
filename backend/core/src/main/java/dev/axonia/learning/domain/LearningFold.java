package dev.axonia.learning.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;

import dev.axonia.expertise.api.CommunityAreas;
import dev.axonia.learning.api.HeldArea;
import dev.axonia.learning.api.WantedArea;

/** Reads intents stored as typed through the community's merges: one area per canonical, one member per area. */
public final class LearningFold {

    private static final BinaryOperator<Instant> EARLIER = BinaryOperator.minBy(Comparator.naturalOrder());

    private LearningFold() {
    }

    /** One member's areas, each at the earliest date they stated any spelling of it, earliest first. */
    public static List<HeldArea> held(List<LearningIntent> intents, CommunityAreas areas) {

        var firstByArea = new HashMap<String, Instant>();
        for (var intent : intents) {
            firstByArea.merge(areas.canonicalOf(intent.tag()), intent.declaredAt(), EARLIER);
        }
        return firstByArea.entrySet().stream().map(e -> new HeldArea(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(HeldArea::declaredAt).thenComparing(HeldArea::tag)).toList();
    }

    /** Every area wanted, counting each member once however many spellings they typed, most wanted first. */
    public static List<WantedArea> wanted(List<LearningIntent> intents, CommunityAreas areas,
            Map<String, Integer> canBeAsked) {

        var learnersByArea = new HashMap<String, Set<String>>();
        for (var intent : intents) {
            learnersByArea.computeIfAbsent(areas.canonicalOf(intent.tag()), area -> new HashSet<>())
                    .add(intent.userId());
        }
        return learnersByArea.entrySet().stream()
                .map(e -> new WantedArea(e.getKey(), e.getValue().size(), canBeAsked.getOrDefault(e.getKey(), 0)))
                .sorted(Comparator.comparingInt(WantedArea::learners).reversed().thenComparing(WantedArea::tag))
                .toList();
    }

    /** Each member holding the area, at the earliest date they stated any spelling of it, earliest first. */
    public static List<Map.Entry<String, Instant>> learners(List<LearningIntent> intents, CommunityAreas areas,
            String area) {

        var firstByUser = new HashMap<String, Instant>();
        for (var intent : intents) {
            if (areas.canonicalOf(intent.tag()).equals(area)) {
                firstByUser.merge(intent.userId(), intent.declaredAt(), EARLIER);
            }
        }
        return firstByUser.entrySet().stream().map(e -> Map.entry(e.getKey(), e.getValue()))
                .sorted(Map.Entry.<String, Instant>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .toList();
    }
}
