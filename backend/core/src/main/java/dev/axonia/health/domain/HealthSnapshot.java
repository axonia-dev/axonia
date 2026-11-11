package dev.axonia.health.domain;

import java.time.Instant;
import java.util.List;

import lombok.Builder;

/**
 * The computed, point-in-time read of a community's vitality over a window. Assembled on demand, persisted nowhere.
 * Deliberately a panel of independent dials, each question with its own status - never a single composite score.
 * Carries no userId anywhere.
 */
@Builder(toBuilder = true)
public record HealthSnapshot(String window, Instant generatedAt, StageDial stage, CurationDial curation,
        CadenceDial cadence, PeopleDial people, EngagementDial engagement, MembershipGrowthDial membershipGrowth,
        CrossTeamReachDial reach, ReuseDial reuse, LearningDial learning, Status payoff, Series series,
        List<Instant> heldSessionDates, Diagnosis diagnosis) {

    public HealthSnapshot {
        heldSessionDates = heldSessionDates == null ? List.of() : List.copyOf(heldSessionDates);
        series = series == null ? Series.empty() : series;
        learning = learning == null ? LearningDial.empty() : learning;
    }

    /**
     * Who carries it, read from talks only: the talks given, the distinct speakers, how few speakers give half the
     * talks, and the anonymous per-speaker shares sorted high-to-low. {@code trend} is speakers against the previous
     * window.
     */
    @Builder
    public record StageDial(long talks, int speakers, int speakersForHalf, List<Double> distribution, Trend trend,
            Status status) {

        public StageDial {
            distribution = distribution == null ? List.of() : List.copyOf(distribution);
        }
    }

    /** Behind the scenes: curation acts (topics proposed, artifacts captured) and the top curator's share. */
    public record CurationDial(long acts, double topShare) {
    }

    /**
     * Is it alive: the rhythm read from held sessions, the next session scheduled and the latest session ever held,
     * each null when there is none.
     */
    @Builder
    public record CadenceDial(CadenceStatus rhythm, int sessionsHeld, Integer medianGapDays, Instant nextSessionAt,
            Instant lastHeldAt, Trend trend, Status status) {
    }

    /**
     * Distinct present people with any act in the window (a talk, a curation act, a topic vote or an attendance), and
     * how many of them are current members. A count, never a share of members.
     */
    public record PeopleDial(long involved, long members, Trend trend) {
    }

    public record EngagementDial(long loves, Trend trend) {
    }

    public record MembershipGrowthDial(long joins, long memberCount, Trend trend) {
    }

    /** Cross-team reach: distinct org-units among speakers. Counts only - never a unit name. */
    public record CrossTeamReachDial(boolean available, int teams, int departments, int businessUnits,
            int speakersWithUnit, int totalSpeakers, Trend trend) {
    }

    /**
     * The reuse funnel, widest first: openings (reach - the artifact was followed out to), then demand (saves,
     * self-saves excluded), then applied value (helped/notHelped answers).
     *
     * <p>
     * Each half keeps its own trend. {@code savesTrend} is the chip that shipped with 0018 and still means exactly what
     * it meant then; repointing it at openings would have redefined a live number without saying so.
     *
     * <p>
     * An opening is reach and never evidence that knowledge was applied. Only {@code helped} carries that claim.
     */
    @Builder
    public record ReuseDial(long opened, long artifactsOpened, Trend openingsTrend, long saves, long artifactsSaved,
            long helped, long notHelped, Trend savesTrend, Status status) {
    }

    /**
     * The activity chart over the window, twelve equal buckets whatever the window's length: talks given, distinct
     * present people involved, and joins.
     *
     * <p>
     * There is deliberately no member-count series: a leave hard-deletes the membership row, so a historical member
     * count is unknowable and only the joins flow can be drawn.
     */
    public record Series(List<Long> talks, List<Long> people, List<Long> joins) {

        /**
         * Three empty series. What a snapshot assembled without one carries, so the wire format is always three arrays
         * a reader can draw nothing from, never a null a reader has to guard.
         */
        public static Series empty() {

            return new Series(List.of(), List.of(), List.of());
        }

        public Series {
            talks = List.copyOf(talks);
            people = List.copyOf(people);
            joins = List.copyOf(joins);
        }
    }
}
