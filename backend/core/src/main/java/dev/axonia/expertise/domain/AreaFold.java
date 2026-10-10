package dev.axonia.expertise.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Folds the spellings a community carries onto its canonical areas. Both reads of the areas fold through it, so the
 * search's list and the curation page's vocabulary cannot drift apart: a spelling one person carries counts them, a
 * spelling nobody visible carries still names its area.
 */
public final class AreaFold {

    private final Map<String, String> aliasMap;
    private final Map<String, Area> areas = new LinkedHashMap<>();

    public AreaFold(Map<String, String> aliasMap) {

        this.aliasMap = Map.copyOf(aliasMap);
    }

    /** The spellings the community produced, whoever carries them, with nobody behind them. */
    public void carryNames(Collection<String> tags) {

        tags.forEach(tag -> areaOf(tag).add(tag));
    }

    public void carrySignals(Collection<UserTagAggregate> rows) {

        rows.forEach(row -> areaOf(row.tag()).add(row.tag(), row.userId()));
    }

    public void carryDeclarations(Collection<Declaration> declarations) {

        declarations.forEach(declaration -> areaOf(declaration.tag()).add(declaration.tag(), declaration.userId()));
    }

    /** What has been folded, named by canonical and sorted by it, narrowed to {@code search} when there is one. */
    public List<ExpertiseArea> folded(String search) {

        var needle = search == null || search.isBlank() ? null : search.toLowerCase(Locale.ROOT);
        return areas.entrySet().stream().filter(entry -> needle == null || entry.getKey().contains(needle))
                .map(entry -> entry.getValue().named(entry.getKey()))
                .sorted(Comparator.comparing(ExpertiseArea::canonicalTag)).toList();
    }

    private Area areaOf(String tag) {

        return areas.computeIfAbsent(AliasResolver.canonicalOf(tag, aliasMap), canonical -> new Area());
    }

    /** The spellings one area is carried under here, and the people carrying them. */
    private static final class Area {

        private final Set<String> variants = new TreeSet<>();
        private final Set<String> people = new HashSet<>();

        void add(String tag) {

            variants.add(tag);
        }

        void add(String tag, String userId) {

            variants.add(tag);
            people.add(userId);
        }

        ExpertiseArea named(String canonical) {

            return new ExpertiseArea(canonical, List.copyOf(variants), people.size());
        }
    }
}
