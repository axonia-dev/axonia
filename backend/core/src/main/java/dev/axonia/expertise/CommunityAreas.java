package dev.axonia.expertise;

import java.util.Map;
import java.util.Set;

import dev.axonia.expertise.internal.AliasResolver;

/** How one community reads its names: which spelling is which area. Records stored as typed are read through it. */
public record CommunityAreas(Map<String, String> aliases) {

    public CommunityAreas {
        aliases = Map.copyOf(aliases);
    }

    public String canonicalOf(String normalizedTag) {

        return AliasResolver.canonicalOf(normalizedTag, aliases);
    }

    public Set<String> variantsOf(String canonical) {

        return AliasResolver.variantsOf(canonical, aliases);
    }
}
