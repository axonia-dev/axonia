package dev.axonia.expertise.domain;

import java.util.Map;
import java.util.Set;

import dev.axonia.expertise.api.CommunityAreas;

/** Expertise's own entry to the alias-folding rule {@link CommunityAreas} holds, for callers holding a bare map. */
public final class AliasResolver {

    private AliasResolver() {
    }

    public static String canonicalOf(String normalizedTag, Map<String, String> variantToCanonical) {

        return CommunityAreas.canonicalOf(normalizedTag, variantToCanonical);
    }

    public static Set<String> variantsOf(String canonical, Map<String, String> variantToCanonical) {

        return CommunityAreas.variantsOf(canonical, variantToCanonical);
    }
}
