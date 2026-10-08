package dev.axonia.health;

import java.time.Instant;
import java.util.List;

/** Read-only signals from the community context. Implemented in dev.axonia.community.internal. */
public interface MembershipSignals {

    long communityIdForSlug(String slug);

    long memberCount(long communityId);

    /** Memberships whose joined_at falls in [from, to). */
    long joins(long communityId, Instant from, Instant to);

    /**
     * Joins per bucket over [from, to), zero-filled.
     *
     * <p>
     * Joins and not the member count on purpose: a leave hard-deletes the membership row, so the historical member
     * count is unknowable and a member-count series would be a fabrication.
     */
    List<Long> joinsPerBucket(long communityId, Instant from, Instant to, int buckets);

    boolean canViewHealth(long communityId, String userId);
}
