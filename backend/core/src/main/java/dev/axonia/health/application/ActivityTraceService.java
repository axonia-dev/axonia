package dev.axonia.health.application;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Service;

import dev.axonia.health.api.spi.ArtifactSignals;
import dev.axonia.health.api.spi.EventsSignals;
import dev.axonia.health.api.spi.MembershipSignals;
import dev.axonia.health.domain.ActivityTrace;
import lombok.RequiredArgsConstructor;

/**
 * Reads a community's activity trace. Open to anyone who can read the community: resolving the slug is the same read
 * the community itself answers, and nothing here asks for the health capability, because the trace interprets nothing.
 */
@Service
@RequiredArgsConstructor
public class ActivityTraceService {

    private final EventsSignals eventsSignals;
    private final ArtifactSignals artifactSignals;
    private final MembershipSignals membershipSignals;
    private final Clock clock;

    public ActivityTrace trace(String slug) {

        var communityId = membershipSignals.communityIdForSlug(slug);
        var now = clock.instant();
        var from = now.minus(Duration.ofDays(ActivityTrace.DAYS));
        var held = ActivityTrace.daysOf(eventsSignals.heldSessionDates(communityId, from, now), from,
                ActivityTrace.DAYS);
        return new ActivityTrace(ActivityTrace.DAYS, held,
                artifactSignals.artifactsPerBucket(communityId, from, now, ActivityTrace.DAYS),
                membershipSignals.joinsPerBucket(communityId, from, now, ActivityTrace.DAYS));
    }
}
