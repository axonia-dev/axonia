package dev.axonia.health.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import dev.axonia.health.api.Buckets;
import dev.axonia.health.api.spi.ArtifactSignals;
import dev.axonia.health.api.spi.EventsSignals;
import dev.axonia.health.api.spi.MembershipSignals;
import dev.axonia.health.domain.HealthSnapshot;
import lombok.RequiredArgsConstructor;

/**
 * The activity chart over the current window only, twelve equal buckets: talks and people involved, each credit
 * filtered by the window's already-resolved present people, and joins. The previous window keeps its scalar comparison
 * and is never bucketed - nothing draws that line.
 */
@RequiredArgsConstructor
final class SnapshotSeries {

    private final EventsSignals eventsSignals;
    private final ArtifactSignals artifactSignals;
    private final MembershipSignals membershipSignals;

    HealthSnapshot.Series of(long communityId, Instant from, Instant to, Set<String> present) {

        var talks = new HashMap<Integer, Long>();
        var people = new HashMap<Integer, Set<String>>();
        eventsSignals.talkCreditsPerBucket(communityId, from, to, Buckets.COUNT).stream()
                .filter(credit -> present.contains(credit.userId())).forEach(credit -> {
                    talks.merge(credit.bucket(), credit.talks(), Long::sum);
                    people.computeIfAbsent(credit.bucket(), bucket -> new HashSet<>()).add(credit.userId());
                });
        Stream.of(eventsSignals.topicCreditsPerBucket(communityId, from, to, Buckets.COUNT),
                artifactSignals.artifactCreditsPerBucket(communityId, from, to, Buckets.COUNT),
                eventsSignals.engagementCreditsPerBucket(communityId, from, to, Buckets.COUNT)).flatMap(List::stream)
                .filter(credit -> present.contains(credit.userId())).forEach(credit -> people
                        .computeIfAbsent(credit.bucket(), bucket -> new HashSet<>()).add(credit.userId()));

        return new HealthSnapshot.Series(Buckets.zeroFill(talks, Buckets.COUNT),
                Buckets.zeroFill(sizes(people), Buckets.COUNT),
                membershipSignals.joinsPerBucket(communityId, from, to, Buckets.COUNT));
    }

    private static Map<Integer, Long> sizes(Map<Integer, Set<String>> people) {

        var sizes = new HashMap<Integer, Long>();
        people.forEach((bucket, ids) -> sizes.put(bucket, (long) ids.size()));
        return sizes;
    }
}
