package dev.axonia.expertise;

import java.time.Instant;

/**
 * One area on the topic map, with everything the client needs to derive every coverage reading without a second call.
 *
 * <p>
 * An area carried only by declarations has no activity to describe, so {@code topHolderName} and {@code lastActivityAt}
 * are both null there and every coverage reading skips it.
 *
 * @param topHolderShare
 *            the largest single practitioner's share of {@code signalCount}, between 0 and 1
 * @param topHolderName
 *            the largest single practitioner's display name, or null when the area has no activity
 * @param lastActivityAt
 *            the most recent signal on the area, or null when the area has no activity
 * @param openToQuestionsCount
 *            distinct people who declared the area in this community, practitioners included, after every privacy
 *            choice. Not an offer count: {@code offeredCount} above is OFFERED signals.
 */
public record TopicNode(String canonicalTag, int practitionerCount, int signalCount, double topHolderShare,
        String topHolderName, int presentedCount, int contributedCount, int offeredCount, Instant lastActivityAt,
        int openToQuestionsCount) {
}
