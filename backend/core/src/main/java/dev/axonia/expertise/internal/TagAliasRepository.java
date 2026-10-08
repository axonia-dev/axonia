package dev.axonia.expertise.internal;

import java.util.Collection;
import java.util.Map;

/**
 * The community's tag vocabulary: which variant tags fold into which canonical. Every read and write is bounded to one
 * community, so a curator can never reshape canonicalization for a community they do not steward.
 */
public interface TagAliasRepository {

    /**
     * Returns a map from variant_tag to canonical_tag for one community.
     */
    Map<String, String> aliasMap(String organizationId, long communityId);

    /**
     * One alias map per requested community, for callers that span communities. The self-view is the only one: it
     * canonicalizes each signal through the vocabulary of the community that signal came from, because the same raw tag
     * may legitimately resolve differently in two communities. A community with no aliases yields an empty map.
     */
    Map<Long, Map<String, String>> aliasMaps(String organizationId, Collection<Long> communityIds);

    /**
     * Inserts or updates the alias so that {@code variantTag} resolves to {@code canonicalTag} in this community.
     */
    void addAlias(String organizationId, long communityId, String variantTag, String canonicalTag);

    /**
     * Removes the alias row for {@code variantTag} in this community (no-op if absent).
     */
    void removeAlias(String organizationId, long communityId, String variantTag);

    /**
     * Removes every alias row in this community that names {@code tag} on either side. A rename or a delete ends the
     * name, so the rows that spoke about it stop meaning anything; left behind, one would quietly fold a later tag of
     * that name into a canonical the community never chose for it.
     */
    void removeAliasesNaming(String organizationId, long communityId, String tag);
}
