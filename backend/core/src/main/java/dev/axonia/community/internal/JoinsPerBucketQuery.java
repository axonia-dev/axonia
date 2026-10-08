package dev.axonia.community.internal;

import lombok.Builder;

/**
 * The four values that scope and shape a joins-per-bucket query, bundled into a record because PMD refuses a method
 * with five or more parameters and this repo's convention is a well-named parameter record rather than a suppression.
 *
 * <p>
 * {@code from} and {@code to} are deliberately NOT components here. They travel as their own plain query parameters,
 * because SpEL binding ({@code :#{#q.from}}) bypasses the {@code Instant} converter and this repo has already been
 * bitten by that.
 */
@Builder
record JoinsPerBucketQuery(String organizationId, Long communityId, double bucketSeconds, int lastBucket) {
}
