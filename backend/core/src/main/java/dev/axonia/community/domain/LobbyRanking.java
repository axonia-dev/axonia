package dev.axonia.community.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import dev.axonia.community.api.LobbyShelfKey;

/**
 * Which communities the lobby features and on which shelf each one sits. The billboard features communities the caller
 * has not joined, or the caller's own when none is left to discover; a community sits on at most one discovery shelf,
 * in the order this week, growing, others. An organization of {@value #COLLAPSE_AT} communities or fewer collapses the
 * discovery shelves into one.
 */
public final class LobbyRanking {

    static final int BILLBOARD_SIZE = 5;
    static final int COLLAPSE_AT = 6;
    private static final Duration WEEK = Duration.ofDays(7);

    private static final Comparator<LobbyCandidate> BY_NAME = Comparator
            .comparing(LobbyCandidate::name, String.CASE_INSENSITIVE_ORDER).thenComparingLong(LobbyCandidate::id);

    private final Instant now;
    private final List<LobbyCandidate> all;
    private final List<LobbyCandidate> billboard;
    private final Map<LobbyShelfKey, List<LobbyCandidate>> shelves;

    private LobbyRanking(List<LobbyCandidate> all, Instant now) {

        this.now = now;
        this.all = List.copyOf(all);
        var unjoined = all.stream().filter(Predicate.not(LobbyCandidate::joined)).toList();
        var featured = unjoined.isEmpty() ? all : unjoined;
        this.billboard = List.copyOf(featured.stream().sorted(billboardOrder()).limit(BILLBOARD_SIZE).toList());
        this.shelves = Collections.unmodifiableMap(shelve(all, unjoined));
    }

    public static LobbyRanking of(List<LobbyCandidate> all, Instant now) {

        return new LobbyRanking(all, now);
    }

    public List<LobbyCandidate> billboard() {

        return billboard;
    }

    public Map<LobbyShelfKey, List<LobbyCandidate>> shelves() {

        return shelves;
    }

    /** The given communities, joined or not, by name; an id the ranking does not hold is ignored. */
    public List<LobbyCandidate> byName(Set<Long> ids) {

        return all.stream().filter(candidate -> ids.contains(candidate.id())).sorted(BY_NAME).toList();
    }

    /**
     * Whether the community's next session starts before {@code now + 7 days}. It is never over, so one that started
     * before {@code now} is under way, and this week.
     */
    public boolean thisWeek(LobbyCandidate candidate) {

        var at = candidate.nextSessionAt();
        return at != null && at.isBefore(now.plus(WEEK));
    }

    private Comparator<LobbyCandidate> billboardOrder() {

        return Comparator.comparingLong(LobbyCandidate::joinedLast30Days).reversed()
                .thenComparing(this::thisWeek, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingLong(LobbyCandidate::memberCount).reversed()).thenComparing(BY_NAME);
    }

    private Map<LobbyShelfKey, List<LobbyCandidate>> shelve(List<LobbyCandidate> all, List<LobbyCandidate> unjoined) {

        var result = new LinkedHashMap<LobbyShelfKey, List<LobbyCandidate>>();
        put(result, LobbyShelfKey.JOINED, all.stream().filter(LobbyCandidate::joined).sorted(BY_NAME).toList());
        if (all.size() <= COLLAPSE_AT) {
            put(result, LobbyShelfKey.EXPLORE, unjoined.stream().sorted(BY_NAME).toList());
            return result;
        }
        put(result, LobbyShelfKey.THIS_WEEK, unjoined.stream().filter(this::thisWeek)
                .sorted(Comparator.comparing(LobbyCandidate::nextSessionAt).thenComparing(BY_NAME)).toList());
        var later = unjoined.stream().filter(Predicate.not(this::thisWeek)).toList();
        put(result, LobbyShelfKey.GROWING,
                later.stream().filter(LobbyRanking::growing).sorted(
                        Comparator.comparingLong(LobbyCandidate::joinedLast30Days).reversed().thenComparing(BY_NAME))
                        .toList());
        put(result, LobbyShelfKey.OTHERS,
                later.stream().filter(Predicate.not(LobbyRanking::growing)).sorted(BY_NAME).toList());
        return result;
    }

    private static boolean growing(LobbyCandidate candidate) {

        return candidate.joinedLast30Days() > 0;
    }

    private static void put(Map<LobbyShelfKey, List<LobbyCandidate>> shelves, LobbyShelfKey key,
            List<LobbyCandidate> items) {

        if (!items.isEmpty()) {
            shelves.put(key, items);
        }
    }
}
