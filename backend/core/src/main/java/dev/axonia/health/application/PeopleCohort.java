package dev.axonia.health.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dev.axonia.health.api.spi.ArtifactSignals;
import dev.axonia.health.api.spi.EventsSignals;
import dev.axonia.health.api.spi.PresenceSignals;
import lombok.RequiredArgsConstructor;

/**
 * Resolves one window's people with a single presence check over everyone who acted in it: talk presenters, topic
 * proposers, artifact contributors and engaged users. Filtering the union once is what keeps talks, speakers, half of
 * the talks, curation, reach and people involved telling the same story: a community's coverage is the people who could
 * still answer a question, and a departed person leaves every figure together.
 */
@RequiredArgsConstructor
final class PeopleCohort {

    private final EventsSignals eventsSignals;
    private final ArtifactSignals artifactSignals;
    private final PresenceSignals presenceSignals;

    Cohort of(String organizationId, long communityId, Instant from, Instant to) {

        var talks = eventsSignals.talkCounts(communityId, from, to);
        var curation = new HashMap<>(eventsSignals.topicProposals(communityId, from, to));
        artifactSignals.artifactCounts(communityId, from, to)
                .forEach((userId, count) -> curation.merge(userId, count, Long::sum));
        var candidates = new HashSet<>(talks.keySet());
        candidates.addAll(curation.keySet());
        candidates.addAll(eventsSignals.engagedUserIds(communityId, from, to));

        var present = presenceSignals.presentOf(organizationId, candidates);
        candidates.retainAll(present);
        return new Cohort(presentOnly(talks, present), presentOnly(curation, present), candidates);
    }

    private static Map<String, Long> presentOnly(Map<String, Long> counts, Set<String> present) {

        return counts.entrySet().stream().filter(entry -> present.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** One window's present people: talks per speaker, curation acts per curator, and everyone involved. */
    record Cohort(Map<String, Long> talks, Map<String, Long> curation, Set<String> involved) {

        Cohort {
            talks = Map.copyOf(talks);
            curation = Map.copyOf(curation);
            involved = Set.copyOf(involved);
        }
    }
}
