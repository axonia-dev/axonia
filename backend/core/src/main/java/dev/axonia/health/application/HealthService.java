package dev.axonia.health.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import dev.axonia.health.api.spi.ArtifactSignals;
import dev.axonia.health.api.spi.DirectorySignals;
import dev.axonia.health.api.spi.EventsSignals;
import dev.axonia.health.api.spi.MembershipSignals;
import dev.axonia.health.api.spi.OpeningSignals;
import dev.axonia.health.api.spi.PresenceSignals;
import dev.axonia.health.api.spi.RecognitionSignals;
import dev.axonia.health.api.spi.ReuseSignals;
import dev.axonia.health.application.PeopleCohort.Cohort;
import dev.axonia.health.application.port.out.CurrentUser;
import dev.axonia.health.application.port.out.OrganizationContext;
import dev.axonia.health.domain.CadenceClassifier;
import dev.axonia.health.domain.Curation;
import dev.axonia.health.domain.DiagnosisEngine;
import dev.axonia.health.domain.Distribution;
import dev.axonia.health.domain.HealthSnapshot;
import dev.axonia.health.domain.Reach;
import dev.axonia.health.domain.StatusRules;
import dev.axonia.health.domain.Trend;
import dev.axonia.shared.api.error.ForbiddenException;
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
        var people = new PeopleCohort(eventsSignals, artifactSignals, presenceSignals);
        var cohorts = new Cohorts(people.of(organizationId, communityId, window.from(), window.now()),
                people.of(organizationId, communityId, window.previousFrom(), window.from()));

        var snapshot = dialsOf(new Scope(organizationId, communityId, window), cohorts);
        return snapshot.toBuilder().diagnosis(DiagnosisEngine.diagnose(snapshot)).build();
    }

    /**
     * The dials, computed against one already-resolved window and the two already-filtered cohorts it spans. Split out
     * of {@link #snapshot(String, int)} because that method has a second, unrelated job - resolving who is allowed to
     * look and over what window.
     */
    private HealthSnapshot dialsOf(Scope scope, Cohorts cohorts) {

        var window = scope.window();
        var communityId = scope.communityId();
        var peopleTrend = Trend.of(cohorts.current().involved().size(), cohorts.previous().involved().size());
        var memberCount = membershipSignals.memberCount(communityId);

        var loves = recognitionSignals.lovesGiven(communityId, window.from(), window.now());
        var previousLoves = recognitionSignals.lovesGiven(communityId, window.previousFrom(), window.from());

        var joins = membershipSignals.joins(communityId, window.from(), window.now());
        var previousJoins = membershipSignals.joins(communityId, window.previousFrom(), window.from());

        var heldDates = eventsSignals.heldSessionDates(communityId, window.from(), window.now());
        var curation = Curation.of(cohorts.current().curation().values());

        return HealthSnapshot.builder().window(window.days() + "d").generatedAt(window.now()).stage(stageOf(cohorts))
                .curation(new HealthSnapshot.CurationDial(curation.acts(), curation.topShare()))
                .cadence(cadenceOf(communityId, window, heldDates, peopleTrend))
                .people(peopleOf(communityId, cohorts.current().involved(), peopleTrend))
                .engagement(new HealthSnapshot.EngagementDial(loves, Trend.of(loves, previousLoves)))
                .membershipGrowth(
                        new HealthSnapshot.MembershipGrowthDial(joins, memberCount, Trend.of(joins, previousJoins)))
                .reach(reachOf(scope.organizationId(), cohorts)).reuse(reuseOf(communityId, window))
                .series(new SnapshotSeries(eventsSignals, artifactSignals, membershipSignals).of(communityId,
                        window.from(), window.now(), cohorts.current().involved()))
                .heldSessionDates(heldDates).build();
    }

    private static HealthSnapshot.StageDial stageOf(Cohorts cohorts) {

        var stage = Distribution.of(cohorts.current().talks().values());
        var previousSpeakers = Distribution.of(cohorts.previous().talks().values()).speakers();
        return HealthSnapshot.StageDial.builder().talks(stage.talks()).speakers(stage.speakers())
                .speakersForHalf(stage.speakersForHalf()).distribution(stage.shares())
                .trend(Trend.of(stage.speakers(), previousSpeakers)).status(StatusRules.stage(stage)).build();
    }

    private HealthSnapshot.CadenceDial cadenceOf(long communityId, Window window, List<Instant> heldDates,
            Trend peopleTrend) {

        var previousHeld = eventsSignals.heldSessionDates(communityId, window.previousFrom(), window.from()).size();
        var nextSessionAt = eventsSignals.nextSessionAt(communityId, window.now()).orElse(null);
        var lastHeldAt = eventsSignals.lastHeldSessionAt(communityId, window.now()).orElse(null);
        var cadence = CadenceClassifier.classify(heldDates, eventsSignals.activeSeries(communityId), nextSessionAt,
                window.now());
        return HealthSnapshot.CadenceDial.builder().rhythm(cadence.status()).sessionsHeld(cadence.sessionsHeld())
                .medianGapDays(cadence.medianGapDays()).nextSessionAt(nextSessionAt).lastHeldAt(lastHeldAt)
                .trend(Trend.of(heldDates.size(), previousHeld))
                .status(StatusRules.alive(cadence.status(), lastHeldAt, nextSessionAt != null, peopleTrend.direction()))
                .build();
    }

    private HealthSnapshot.PeopleDial peopleOf(long communityId, Set<String> involved, Trend trend) {

        var members = membershipSignals.membersAmong(communityId, involved).size();
        return new HealthSnapshot.PeopleDial(involved.size(), members, trend);
    }

    private HealthSnapshot.CrossTeamReachDial reachOf(String organizationId, Cohorts cohorts) {

        var reach = reachOf(organizationId, cohorts.current().talks().keySet());
        var previousReach = reachOf(organizationId, cohorts.previous().talks().keySet());
        return new HealthSnapshot.CrossTeamReachDial(reach.available(), reach.teams(), reach.departments(),
                reach.businessUnits(), reach.speakersWithUnit(), reach.totalSpeakers(),
                Trend.of(reach.teams(), previousReach.teams()));
    }

    private Reach reachOf(String organizationId, Set<String> speakerIds) {

        var units = directorySignals.unitsOf(organizationId, speakerIds);
        return Reach.of(units.values(), speakerIds.size());
    }

    private HealthSnapshot.ReuseDial reuseOf(long communityId, Window window) {

        var reuse = reuseSignals.reuseCounts(communityId, window.from(), window.now());
        var previousReuse = reuseSignals.reuseCounts(communityId, window.previousFrom(), window.from());
        var openings = openingSignals.openingCounts(communityId, window.from(), window.now());
        var previousOpenings = openingSignals.openingCounts(communityId, window.previousFrom(), window.from());
        return HealthSnapshot.ReuseDial.builder().opened(openings.openings())
                .artifactsOpened(openings.distinctArtifactsOpened())
                .openingsTrend(Trend.of(openings.openings(), previousOpenings.openings())).saves(reuse.saves())
                .artifactsSaved(reuse.distinctArtifactsSaved()).helped(reuse.helped()).notHelped(reuse.notHelped())
                .savesTrend(Trend.of(reuse.saves(), previousReuse.saves()))
                .status(StatusRules.payoff(openings.openings(), reuse.saves(), reuse.helped())).build();
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

    private record Scope(String organizationId, long communityId, Window window) {
    }

    private record Cohorts(Cohort current, Cohort previous) {
    }
}
