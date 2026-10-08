package dev.axonia.expertise.internal;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class AliasResolver {

    private AliasResolver() {
    }

    /**
     * Returns the canonical tag for the given normalized tag, or the tag itself if it has no mapping.
     */
    public static String canonicalOf(String normalizedTag, Map<String, String> variantToCanonical) {

        return variantToCanonical.getOrDefault(normalizedTag, normalizedTag);
    }

    /**
     * Returns the canonical tag plus all variants that point to it.
     */
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
