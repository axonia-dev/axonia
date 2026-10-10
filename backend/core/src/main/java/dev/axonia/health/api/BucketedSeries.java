package dev.axonia.health.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import dev.axonia.shared.api.persistence.BucketCountQuery;
import lombok.Builder;

/**
 * The shared shape of one grouped, zero-filled bucket-count read. Every read-only signal port that answers a whole
 * series in a single query asks for the same thing - tenant, community, window and bucket count - and reduces the same
 * sparse {@code bucket -> n} counts to a dense series. That shape lives here, beside {@link Buckets} rather than inside
 * any one adapter, because five modules across five bounded contexts each implement one such port and none of them owns
 * the others. The query's bind values and row shape are {@link BucketCountQuery}'s, in {@code shared}; each adapter
 * runs its own query.
 */
public final class BucketedSeries {

    /**
     * What a bucketed read covers: the tenant, the community, the window, and the number of buckets that window is cut
     * into. The count drives both the width of a bucket and the index of the last one, so a series is only ever as
     * long, and as coarse, as its caller asked for.
     */
    @Builder
    public record Read(String organizationId, long communityId, Instant from, Instant to, int buckets) {

        /** The query this read runs, its bucket width and last index derived from the window and the count. */
        public BucketCountQuery query() {

            return BucketCountQuery.builder().organizationId(organizationId).communityId(communityId).from(from).to(to)
                    .bucketSeconds(Buckets.secondsPerBucket(from, to, buckets)).lastBucket(buckets - 1).build();
        }

        /** Zero-fills the sparse counts by bucket index into a dense series of exactly {@code buckets} entries. */
        public List<Long> series(Map<Integer, Long> countsByBucket) {

            return Buckets.zeroFill(countsByBucket, buckets);
        }
    }

    private BucketedSeries() {
    }
}
