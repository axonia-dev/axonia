package dev.axonia.expertise.internal;

import java.util.Set;

/**
 * The pairs one community has parked as legitimately distinct. A verdict is a fact about the community that reached it,
 * never about the organization.
 */
public interface TagDismissalRepository {

    /**
     * An order-normalized parked pair: {@code tagLow} sorts lexicographically before or equal to {@code tagHigh}.
     */
    record TagPair(String tagLow, String tagHigh) {
    }

    /**
     * Returns every parked ("won't do") tag pair for one community.
     */
    Set<TagPair> dismissedPairs(String organizationId, long communityId);

    /**
     * Records that {@code tagLow}/{@code tagHigh} are legitimately distinct in this community (idempotent). Callers
     * pass tags already normalized and sorted so {@code tagLow <= tagHigh}.
     */
    void addDismissal(String organizationId, long communityId, String tagLow, String tagHigh);

    /**
     * Removes the parked pair for this community (no-op if absent).
     */
    void removeDismissal(String organizationId, long communityId, String tagLow, String tagHigh);

    /**
     * Removes every parked pair of this community that names {@code tag} on either side. A verdict about a tag that no
     * longer exists is not a verdict at all.
     */
    void removeDismissalsNaming(String organizationId, long communityId, String tag);
}
