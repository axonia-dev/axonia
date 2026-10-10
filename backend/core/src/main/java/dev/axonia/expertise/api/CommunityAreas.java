package dev.axonia.expertise.api;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * How one community reads its names: which spelling is which area. Records stored as typed are read through it. The
 * folding rule lives here once; expertise's own {@code AliasResolver} applies the same statics.
 */
public record CommunityAreas(Map<String, String> aliases) {

    public CommunityAreas {
        aliases = Map.copyOf(aliases);
    }

    public String canonicalOf(String normalizedTag) {

        return canonicalOf(normalizedTag, aliases);
    }

    public Set<String> variantsOf(String canonical) {

        return variantsOf(canonical, aliases);
    }

    /** The canonical tag for the given normalized tag, or the tag itself if it has no mapping. */
    public static String canonicalOf(String normalizedTag, Map<String, String> variantToCanonical) {

        return variantToCanonical.getOrDefault(normalizedTag, normalizedTag);
    }

    /** The canonical tag plus all variants that point to it. */
    public static Set<String> variantsOf(String canonical, Map<String, String> variantToCanonical) {

        var result = new HashSet<String>();
        result.add(canonical);
        for (var entry : variantToCanonical.entrySet()) {
            if (canonical.equals(entry.getValue())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
}
