package dev.axonia.health;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.axonia.ForbiddenException;
import dev.axonia.health.internal.CadenceClassifier;
import dev.axonia.health.internal.DiagnosisEngine;
import dev.axonia.health.internal.Distribution;
import dev.axonia.health.internal.Reach;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final EventsSignals eventsSignals;
    private final ArtifactSignals artifactSignals;
    private final ReuseSignals reuseSignals;
    private final RecognitionSignals recognitionSignals;
    private final MembershipSignals membershipSignals;
    private final DirectorySignals directorySignals;
    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;
    private final PresenceSignals presenceSignals;
    private final OpeningSignals openingSignals;
    private final Clock clock;

    public HealthSnapshot snapshot(String slug, int windowDays) {

        var communityId = membershipSignals.communityIdForSlug(slug);
        if (!membershipSignals.canViewHealth(communityId, currentUser.userId())) {
            throw new ForbiddenException("Community health is not open to you");
        }

        var window = Window.of(clock.instant(), windowDays);
        var organizationId = organizationContext.organizationId();
        var cohorts = new Cohorts(cohortOf(organizationId, communityId, window.from(), window.now()),
                cohortOf(organizationId, communityId, window.previousFrom(), window.from()));

        var snapshot = dialsOf(communityId, organizationId, window, cohorts);
        return snapshot.toBuilder().diagnosis(DiagnosisEngine.diagnose(snapshot)).build();
    }

    /**
     * The five dials, computed against one already-resolved window and the two already-filtered cohorts it spans. Split
     * out of {@link #snapshot(String, int)} because that method has a second, unrelated job - resolving who is allowed
     * to look and over what window - and folding both into one method obscured where the dial math starts.
     */
    private HealthSnapshot dialsOf(long communityId, String organizationId, Window window, Cohorts cohorts) {

        var currentCohort = cohorts.current();
        var previousCohort = cohorts.previous();
        var contributions = currentCohort.contributions();
        var previousContributions = previousCohort.contributions();

        var reach = reachOf(organizationId, contributions.keySet());
        var previousReach = reachOf(organizationId, previousContributions.keySet());

        var distribution = Distribution.of(contributions.values());

        var heldDates = eventsSignals.heldSessionDates(communityId, window.from(), window.now());
        var previousHeld = eventsSignals.heldSessionDates(communityId, window.previousFrom(), window.from()).size();
        var cadence = CadenceClassifier.classify(heldDates, eventsSignals.activeSeries(communityId), window.now());

        var memberCount = membershipSignals.memberCount(communityId);
        var active = currentCohort.active();
        var previousActive = previousCohort.active();
        // Leaves hard-delete the membership row, so the historical member count is
        // unknowable; both windows read against today's denominator.
        var share = shareOf(active.size(), memberCount);
        var previousShare = shareOf(previousActive.size(), memberCount);

        var loves = recognitionSignals.lovesGiven(communityId, window.from(), window.now());
        var previousLoves = recognitionSignals.lovesGiven(communityId, window.previousFrom(), window.from());

        var joins = membershipSignals.joins(communityId, window.from(), window.now());
        var previousJoins = membershipSignals.joins(communityId, window.previousFrom(), window.from());

        var reuse = reuseSignals.reuseCounts(communityId, window.from(), window.now());
        var previousReuse = reuseSignals.reuseCounts(communityId, window.previousFrom(), window.from());

        var openings = openingSignals.openingCounts(communityId, window.from(), window.now());
        var previousOpenings = openingSignals.openingCounts(communityId, window.previousFrom(), window.from());

        return HealthSnapshot.builder().window(window.days() + "d").generatedAt(window.now())
                .busFactor(distribution.busFactor())
                .concentration(new HealthSnapshot.Concentration(distribution.top3Share(), distribution.shares()))
                .cadence(new HealthSnapshot.CadenceDial(cadence.status(), cadence.sessionsHeld(),
                        cadence.medianGapDays(), Trend.of(heldDates.size(), previousHeld)))
                .participation(new HealthSnapshot.ParticipationDial(active.size(), memberCount, share,
                        Trend.of(share, previousShare)))
                .engagement(new HealthSnapshot.EngagementDial(loves, Trend.of(loves, previousLoves)))
                .membershipGrowth(
                        new HealthSnapshot.MembershipGrowthDial(joins, memberCount, Trend.of(joins, previousJoins)))
                .reach(new HealthSnapshot.CrossTeamReachDial(reach.available(), reach.teams(), reach.departments(),
                        reach.businessUnits(), reach.contributorsWithUnit(), reach.totalContributors(),
                        Trend.of(reach.teams(), previousReach.teams())))
                .reuse(HealthSnapshot.ReuseDial.builder().opened(openings.openings())
                        .artifactsOpened(openings.distinctArtifactsOpened())
                        .openingsTrend(Trend.of(openings.openings(), previousOpenings.openings())).saves(reuse.saves())
                        .artifactsSaved(reuse.distinctArtifactsSaved()).helped(reuse.helped())
                        .notHelped(reuse.notHelped()).savesTrend(Trend.of(reuse.saves(), previousReuse.saves()))
                        .build())
                .series(seriesOf(communityId, window)).heldSessionDates(heldDates).build();
    }

    /**
     * Each dial's shape over the current window only, twelve equal buckets. The previous window keeps its scalar
     * comparison and is never bucketed - nothing draws that line. Split out of {@link #dialsOf} to keep that method
     * under the complexity ceiling.
     */
    private HealthSnapshot.Series seriesOf(long communityId, Window window) {

        return new HealthSnapshot.Series(
                eventsSignals.distinctContributorsPerBucket(communityId, window.from(), window.now(), Buckets.COUNT),
                eventsSignals.engagedMembersPerBucket(communityId, window.from(), window.now(), Buckets.COUNT),
                recognitionSignals.lovesPerBucket(communityId, window.from(), window.now(), Buckets.COUNT),
                membershipSignals.joinsPerBucket(communityId, window.from(), window.now(), Buckets.COUNT),
                openingSignals.openingsPerBucket(communityId, window.from(), window.now(), Buckets.COUNT),
                reuseSignals.savesPerBucket(communityId, window.from(), window.now(), Buckets.COUNT));
    }

    /**
     * The current and previous instant a window spans, resolved once so every signal in both cohorts reads the same
     * "now".
     */
    private record Window(Instant now, Instant from, Instant previousFrom, int days) {

        static Window of(Instant now, int windowDays) {

            var from = now.minus(Duration.ofDays(windowDays));
            var previousFrom = from.minus(Duration.ofDays(windowDays));
            return new Window(now, from, previousFrom, windowDays);
        }
    }

    private record Cohorts(Cohort current, Cohort previous) {
    }

    /**
     * The present contributors and the present active members of one window, resolved with a single presence check over
     * their combined ids. Filtering the union rather than each dial separately is what keeps bus factor, reach and
     * active-member count telling the same story: a community's coverage is the people who could still answer a
     * question, not everyone who ever contributed or showed up - a departed member cannot re-enter active membership
     * through engagement after being dropped from contribution counts.
     */
    private Cohort cohortOf(String organizationId, long communityId, Instant from, Instant to) {

        var contributions = contributionsPerMember(communityId, from, to);
        var engaged = eventsSignals.engagedUserIds(communityId, from, to);
        var candidates = new HashSet<>(contributions.keySet());
        candidates.addAll(engaged);

        var present = presenceSignals.presentOf(organizationId, candidates);

        var presentContributions = contributions.entrySet().stream().filter(entry -> present.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        var active = new HashSet<>(candidates);
        active.retainAll(present);

        return new Cohort(presentContributions, active);
    }

    private record Cohort(Map<String, Long> contributions, Set<String> active) {

        private Cohort {

            contributions = Map.copyOf(contributions);
            active = Set.copyOf(active);
        }
    }

    private Map<String, Long> contributionsPerMember(long communityId, Instant from, Instant to) {

        var merged = new HashMap<>(eventsSignals.contributionCounts(communityId, from, to));
        artifactSignals.artifactCounts(communityId, from, to)
                .forEach((userId, count) -> merged.merge(userId, count, Long::sum));
        return merged;
    }

    private Reach reachOf(String organizationId, Set<String> contributorIds) {

        var units = directorySignals.unitsOf(organizationId, contributorIds);
        return Reach.of(units.values(), contributorIds.size());
    }

    private static double shareOf(long active, long memberCount) {

        return memberCount == 0 ? 0.0 : (double) active / memberCount;
    }
}
