package dev.axonia.health.domain;

import java.time.Instant;
import java.util.List;

import lombok.Builder;

/**
 * The computed, point-in-time read of a community's vitality over a window. Assembled on demand, persisted nowhere.
 * Deliberately a panel of independent dials — never a single composite score. Carries no userId anywhere.
 */
@Builder(toBuilder = true)
public record HealthSnapshot(String window, Instant generatedAt, int busFactor, Concentration concentration,
        CadenceDial cadence, ParticipationDial participation, EngagementDial engagement,
        MembershipGrowthDial membershipGrowth, CrossTeamReachDial reach, ReuseDial reuse, Series series,
        List<Instant> heldSessionDates, Diagnosis diagnosis) {

    public HealthSnapshot {
        heldSessionDates = heldSessionDates == null ? List.of() : List.copyOf(heldSessionDates);
        series = series == null ? Series.empty() : series;
    }

    /** Anonymous shares sorted high-to-low; identity was dropped before this DTO. */
    public record Concentration(double top3Share, List<Double> distribution) {

        public Concentration {
            distribution = distribution == null ? List.of() : List.copyOf(distribution);
        }
    }

    public record CadenceDial(CadenceStatus status, int sessionsHeld, Integer medianGapDays, Trend trend) {
    }

    public record ParticipationDial(long activeMembers, long totalMembers, double share, Trend trend) {
    }

    public record EngagementDial(long loves, Trend trend) {
    }

    public record MembershipGrowthDial(long joins, long memberCount, Trend trend) {
    }

    /** Cross-team reach: distinct org-units among contributors. Counts only — never a unit name. */
    public record CrossTeamReachDial(boolean available, int teams, int departments, int businessUnits,
            int contributorsWithUnit, int totalContributors, Trend trend) {
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
            long helped, long notHelped, Trend savesTrend) {
    }

    /**
     * Each dial's shape over the window, twelve equal buckets whatever the window's length.
     *
     * <p>
     * There is deliberately no bus-factor series and no member-count series. A bus factor over one bucket is a
     * different statistic that renders sparsity as fragility, so the fragility chapter series the distinct contributors
     * instead; and a leave hard-deletes the membership row, so a historical member count is unknowable and only the
     * joins flow can be drawn.
     *
     * <p>
     * {@code participation} is not the participation dial's own history: it series the engagement acts alone - topic
     * votes and attendance - with no presence filter, while the dial's {@code activeMembers} is the union of
     * contributors and engaged members who are still present. Nothing may label this series as the participation dial's
     * history.
     */
    public record Series(List<Long> contributors, List<Long> participation, List<Long> engagement, List<Long> joins,
            List<Long> openings, List<Long> saves) {

        /**
         * Six empty series. What a snapshot assembled without one carries, so the wire format is always six arrays a
         * reader can draw nothing from, never a null a reader has to guard.
         */
        public static Series empty() {

            return new Series(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        public Series {
            contributors = List.copyOf(contributors);
            participation = List.copyOf(participation);
            engagement = List.copyOf(engagement);
            joins = List.copyOf(joins);
            openings = List.copyOf(openings);
            saves = List.copyOf(saves);
        }
    }
}
