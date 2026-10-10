package dev.axonia.expertise.application.port.out;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import dev.axonia.expertise.domain.TagPair;

/**
 * In-memory {@link TagDismissalRepository} keyed on (organization, community, pair), so a test can assert that a
 * community's parked pair is a verdict of that community alone.
 */
public class FakeTagDismissalRepository implements TagDismissalRepository {

    private final Map<String, Map<Long, Set<TagPair>>> byOrg = new HashMap<>();

    @Override
    public Set<TagPair> dismissedPairs(String organizationId, long communityId) {

        return Set.copyOf(community(organizationId, communityId));
    }

    @Override
    public void addDismissal(String organizationId, long communityId, String tagLow, String tagHigh) {

        community(organizationId, communityId).add(new TagPair(tagLow, tagHigh));
    }

    @Override
    public void removeDismissal(String organizationId, long communityId, String tagLow, String tagHigh) {

        community(organizationId, communityId).remove(new TagPair(tagLow, tagHigh));
    }

    @Override
    public void removeDismissalsNaming(String organizationId, long communityId, String tag) {

        community(organizationId, communityId)
                .removeIf(pair -> pair.tagLow().equals(tag) || pair.tagHigh().equals(tag));
    }

    private Set<TagPair> community(String organizationId, long communityId) {

        return byOrg.computeIfAbsent(organizationId, k -> new HashMap<>()).computeIfAbsent(communityId,
                k -> new HashSet<>());
    }
}
