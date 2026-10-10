package dev.axonia.health.domain;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisEngineTest {

    private static final Trend FLAT = Trend.of(1, 1);
    private static final Trend DOWN = Trend.of(1, 2);

    /** A healthy baseline snapshot; each test tweaks one axis. */
    private HealthSnapshot.HealthSnapshotBuilder healthy() {

        return HealthSnapshot.builder().window("90d").generatedAt(Instant.parse("2026-06-11T12:00:00Z")).busFactor(6)
                .concentration(new HealthSnapshot.Concentration(0.4, List.of(0.2, 0.1, 0.1)))
                .cadence(new HealthSnapshot.CadenceDial(CadenceStatus.STEADY, 6, 14, FLAT))
                .participation(new HealthSnapshot.ParticipationDial(20, 40, 0.5, FLAT))
                .engagement(new HealthSnapshot.EngagementDial(10, FLAT))
                .membershipGrowth(new HealthSnapshot.MembershipGrowthDial(3, 40, FLAT))
                .reach(new HealthSnapshot.CrossTeamReachDial(true, 4, 3, 2, 6, 6, FLAT))
                .reuse(new HealthSnapshot.ReuseDial(0, 0, FLAT, 5, 3, 2, 0, FLAT));
    }

    private List<Finding.Code> codes(HealthSnapshot s) {

        return DiagnosisEngine.diagnose(s).findings().stream().map(Finding::code).toList();
    }

    @Test
    void wellDistributedOnRhythmAndReusedForAHealthyCommunity() {

        assertThat(codes(healthy().build())).containsExactly(Finding.Code.WELL_DISTRIBUTED, Finding.Code.ON_RHYTHM,
                Finding.Code.KNOWLEDGE_REUSED);
    }

    @Test
    void singlePointWhenBusFactorIsOne() {

        var s = healthy().busFactor(1).build();
        assertThat(codes(s)).contains(Finding.Code.SINGLE_POINT).doesNotContain(Finding.Code.WELL_DISTRIBUTED);
    }

    @Test
    void overRelianceWhenBusFactorIsTwo() {

        assertThat(codes(healthy().busFactor(2).build())).contains(Finding.Code.OVER_RELIANCE);
    }

    @Test
    void fadingWhenSlowingAndParticipationDown() {

        var s = healthy().cadence(new HealthSnapshot.CadenceDial(CadenceStatus.SLOWING, 2, 40, FLAT))
                .participation(new HealthSnapshot.ParticipationDial(10, 40, 0.25, DOWN)).build();
        assertThat(codes(s)).contains(Finding.Code.FADING);
    }

    @Test
    void notFadingWhenSlowingButParticipationSteady() {

        var s = healthy().cadence(new HealthSnapshot.CadenceDial(CadenceStatus.SLOWING, 2, 40, FLAT)).build();
        assertThat(codes(s)).doesNotContain(Finding.Code.FADING);
    }

    @Test
    void fadingWhenStalledAndParticipationDown() {

        var s = healthy().cadence(new HealthSnapshot.CadenceDial(CadenceStatus.STALLED, 0, null, FLAT))
                .participation(new HealthSnapshot.ParticipationDial(10, 40, 0.25, DOWN)).build();
        assertThat(codes(s)).contains(Finding.Code.FADING);
    }

    @Test
    void dormantWhenNoActiveSeries() {

        var s = healthy().cadence(new HealthSnapshot.CadenceDial(CadenceStatus.DORMANT, 0, null, FLAT)).build();
        assertThat(codes(s)).contains(Finding.Code.DORMANT).doesNotContain(Finding.Code.ON_RHYTHM);
    }

    @Test
    void socialClubDriftWhenEngagedButNoProductionOrReuse() {

        var s = healthy().busFactor(0).engagement(new HealthSnapshot.EngagementDial(9, FLAT))
                .reuse(new HealthSnapshot.ReuseDial(0, 0, FLAT, 0, 0, 0, 0, FLAT)).build();
        assertThat(codes(s)).contains(Finding.Code.SOCIAL_CLUB_DRIFT);
    }

    @Test
    void socialClubDriftStaysSilentWhenDormant() {

        var s = healthy().busFactor(0).engagement(new HealthSnapshot.EngagementDial(9, FLAT))
                .reuse(new HealthSnapshot.ReuseDial(0, 0, FLAT, 0, 0, 0, 0, FLAT))
                .cadence(new HealthSnapshot.CadenceDial(CadenceStatus.DORMANT, 0, null, FLAT)).build();
        assertThat(codes(s)).doesNotContain(Finding.Code.SOCIAL_CLUB_DRIFT).contains(Finding.Code.DORMANT);
    }

    @Test
    void interestWithoutPayoffWhenSavedButNoneHelped() {

        var s = healthy().reuse(new HealthSnapshot.ReuseDial(0, 0, FLAT, 18, 7, 0, 0, FLAT)).build();
        assertThat(codes(s)).contains(Finding.Code.INTEREST_WITHOUT_PAYOFF)
                .doesNotContain(Finding.Code.KNOWLEDGE_REUSED);
    }

    @Test
    void narrowReachWhenSingleTeamWithMultipleContributors() {

        var s = healthy().reach(new HealthSnapshot.CrossTeamReachDial(true, 1, 1, 1, 6, 6, FLAT)).build();
        assertThat(codes(s)).contains(Finding.Code.NARROW_REACH);
    }

    @Test
    void reachNotFlaggedWhenUnavailable() {

        var s = healthy().reach(new HealthSnapshot.CrossTeamReachDial(false, 0, 0, 0, 0, 6, FLAT)).build();
        assertThat(codes(s)).doesNotContain(Finding.Code.NARROW_REACH);
    }

    @Test
    void findingsAreOrderedRiskThenGapThenStrength() {

        // busFactor 1 (risk) + saves-no-payoff (gap) + steady (strength)
        var s = healthy().busFactor(1).reuse(new HealthSnapshot.ReuseDial(0, 0, FLAT, 18, 7, 0, 0, FLAT)).build();
        var kinds = DiagnosisEngine.diagnose(s).findings().stream().map(Finding::kind).toList();
        assertThat(kinds).containsExactly(Finding.Kind.RISK, Finding.Kind.GAP, Finding.Kind.STRENGTH);
    }
}
