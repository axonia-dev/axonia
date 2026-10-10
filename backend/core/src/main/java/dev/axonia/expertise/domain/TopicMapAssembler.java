package dev.axonia.expertise.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

import lombok.Builder;

/**
 * Folds the per-person aggregates and the community's declarations into the map: raw tags become canonical, so a
 * variant merges into its canonical node and carries its links with it, and each person contributes one link for every
 * pair of areas they carry, practised or declared.
 *
 * <p>
 * Pair emission is bounded by each person's own breadth rather than by the size of the vocabulary, which is what keeps
 * this linear in the number of rows for any realistic community.
 *
 * <p>
 * A named area's stored tags fold through the same aliases, so a merge made after a tag was placed still lands on the
 * node the map draws. A tag the map does not draw is dropped. When a fold makes two named areas claim one canonical,
 * the named area holding the canonical under its own name keeps it, else the lowest id does: the map paints a tag one
 * colour only.
 */
public final class TopicMapAssembler {

    /** Joins a link's two tags into one sortable map key; tags are normalized identifiers and never contain a pipe. */
    private static final String KEY_SEPARATOR = "|";

    private TopicMapAssembler() {
    }

    /** Everything one map is assembled from. */
    @Builder
    public record Sources(List<UserTagAggregate> rows, List<Declaration> declarations, List<NamedArea> namedAreas,
            Map<String, String> aliasMap, UnaryOperator<String> nameOf) {

        public Sources {
            rows = List.copyOf(rows);
            declarations = List.copyOf(declarations);
            namedAreas = List.copyOf(namedAreas);
            aliasMap = Map.copyOf(aliasMap);
        }
    }

    public static TopicMap assemble(Sources sources) {

        var aliasMap = sources.aliasMap();
        var byTag = new TreeMap<String, Accumulator>();
        var byUser = new HashMap<String, List<String>>();

        for (var row : sources.rows()) {
            var canonical = AliasResolver.canonicalOf(row.tag(), aliasMap);
            byTag.computeIfAbsent(canonical, t -> new Accumulator()).add(row);
            carry(byUser, row.userId(), canonical);
        }
        for (var declaration : sources.declarations()) {
            var canonical = AliasResolver.canonicalOf(declaration.tag(), aliasMap);
            byTag.computeIfAbsent(canonical, t -> new Accumulator()).declaredBy(declaration.userId());
            carry(byUser, declaration.userId(), canonical);
        }

        var topics = new ArrayList<TopicNode>();
        byTag.forEach((tag, acc) -> topics.add(acc.toNode(tag, sources.nameOf())));

        return new TopicMap(topics, links(byUser), namedAreas(sources.namedAreas(), aliasMap, byTag.keySet()));
    }

    private static List<NamedArea> namedAreas(List<NamedArea> stored, Map<String, String> aliasMap, Set<String> drawn) {

        var ownRows = new HashMap<String, Long>();
        var foldedRows = new HashMap<String, Long>();
        for (var namedArea : stored) {
            for (var tag : namedArea.tags()) {
                var canonical = AliasResolver.canonicalOf(tag, aliasMap);
                var claims = canonical.equals(tag) ? ownRows : foldedRows;
                claims.merge(canonical, namedArea.id(), Math::min);
            }
        }
        var owners = new HashMap<>(foldedRows);
        owners.putAll(ownRows);
        owners.keySet().retainAll(drawn);

        var tagsById = new HashMap<Long, Set<String>>();
        owners.forEach((canonical, id) -> tagsById.computeIfAbsent(id, k -> new TreeSet<>()).add(canonical));
        return stored.stream().map(namedArea -> new NamedArea(namedArea.id(), namedArea.name(),
                List.copyOf(tagsById.getOrDefault(namedArea.id(), Set.of())))).toList();
    }

    /**
     * One entry per person per canonical area, whether they practise it or offered to answer on it. This list is the
     * only input to link emission, so a declaration links areas exactly as practice does.
     */
    private static void carry(Map<String, List<String>> byUser, String userId, String canonical) {

        var tags = byUser.computeIfAbsent(userId, u -> new ArrayList<>());
        if (!tags.contains(canonical)) {
            tags.add(canonical);
        }
    }

    private static List<TopicLink> links(Map<String, List<String>> byUser) {

        var weights = new TreeMap<String, Integer>();
        for (var tags : byUser.values()) {
            for (var i = 0; i < tags.size(); i++) {
                for (var j = i + 1; j < tags.size(); j++) {
                    weights.merge(key(tags.get(i), tags.get(j)), 1, Integer::sum);
                }
            }
        }

        var links = new ArrayList<TopicLink>();
        weights.forEach((key, weight) -> {
            var separatorAt = key.indexOf(KEY_SEPARATOR);
            links.add(new TopicLink(key.substring(0, separatorAt), key.substring(separatorAt + 1), weight));
        });
        return links;
    }

    private static String key(String one, String other) {

        return one.compareTo(other) <= 0 ? one + KEY_SEPARATOR + other : other + KEY_SEPARATOR + one;
    }

    /** Mutable per-area tally; a person's totals arrive as one row each, and a declaration as one user id. */
    private static final class Accumulator {

        private final Map<String, Integer> signalsPerUser = new HashMap<>();
        private final Set<String> declarers = new HashSet<>();
        private int presented;
        private int contributed;
        private int offered;
        private Instant last;

        private void add(UserTagAggregate row) {

            var total = row.presented() + row.contributed() + row.offered();
            signalsPerUser.merge(row.userId(), total, Integer::sum);
            presented += row.presented();
            contributed += row.contributed();
            offered += row.offered();
            if (last == null || row.lastOccurredAt().isAfter(last)) {
                last = row.lastOccurredAt();
            }
        }

        private void declaredBy(String userId) {

            declarers.add(userId);
        }

        private TopicNode toNode(String tag, UnaryOperator<String> nameOf) {

            var total = presented + contributed + offered;
            var top = signalsPerUser.entrySet().stream().max(Map.Entry.<String, Integer>comparingByValue()
                    .thenComparing(Map.Entry.comparingByKey(java.util.Comparator.reverseOrder()))).orElse(null);
            var share = top == null || total == 0 ? 0d : (double) top.getValue() / total;
            var holder = top == null ? null : nameOf.apply(top.getKey());
            return new TopicNode(tag, signalsPerUser.size(), total, share, holder, presented, contributed, offered,
                    last, declarers.size());
        }
    }
}
