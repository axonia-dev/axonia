package dev.axonia.expertise.application.port.out;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory {@link TagAliasRepository} keyed on (organization, community, variant), so a test can assert the thing the
 * community-owned vocabulary buys: an alias written from one community is invisible to another.
 */
public class FakeTagAliasRepository implements TagAliasRepository {

    private final Map<String, Map<Long, Map<String, String>>> byOrg = new HashMap<>();

    @Override
    public Map<String, String> aliasMap(String organizationId, long communityId) {

        return Map.copyOf(community(organizationId, communityId));
    }

    @Override
    public Map<Long, Map<String, String>> aliasMaps(String organizationId, Collection<Long> communityIds) {

        var result = new HashMap<Long, Map<String, String>>();
        for (var communityId : communityIds) {
            result.put(communityId, aliasMap(organizationId, communityId));
        }
        return result;
    }

    @Override
    public void addAlias(String organizationId, long communityId, String variantTag, String canonicalTag) {

        community(organizationId, communityId).put(variantTag, canonicalTag);
    }

    @Override
    public void removeAlias(String organizationId, long communityId, String variantTag) {

        community(organizationId, communityId).remove(variantTag);
    }

    @Override
    public void removeAliasesNaming(String organizationId, long communityId, String tag) {

        community(organizationId, communityId).entrySet()
                .removeIf(entry -> tag.equals(entry.getKey()) || tag.equals(entry.getValue()));
    }

    private Map<String, String> community(String organizationId, long communityId) {

        return byOrg.computeIfAbsent(organizationId, k -> new HashMap<>()).computeIfAbsent(communityId,
                k -> new HashMap<>());
    }
}
