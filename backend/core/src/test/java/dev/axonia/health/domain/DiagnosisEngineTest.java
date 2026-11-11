package dev.axonia.health.domain;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisEngineTest {

    private static final Trend FLAT = Trend.of(1, 1);
    private static final Trend DOWN = Trend.of(1, 2);

    private static HealthSnapshot.StageDial stage(Long... talkCounts) {

        var distribution = Distribution.of(List.of(talkCounts));
        return HealthSnapshot.StageDial.builder().talks(distribution.talks()).speakers(distribution.speakers())
                .speakersForHalf(distribution.speakersForHalf()).distribution(distribution.shares()).trend(FLAT)
                .status(StatusRules.stage(distribution)).build();
    }

    private static HealthSnapshot.CadenceDial cadence(CadenceStatus rhythm, StatusCode status) {

        return HealthSnapshot.CadenceDial.builder().rhythm(rhythm).sessionsHeld(4).medianGapDays(14).trend(FLAT)
                .status(Status.of(status)).build();
    }

    private static HealthSnapshot.ReuseDial reuse(long saves, long helped) {

        return HealthSnapshot.ReuseDial.builder().openingsTrend(FLAT).saves(saves).helped(helped).savesTrend(FLAT)
                .status(StatusRules.payoff(0, saves, helped)).build();
    }

    private static LearningDial learning(long answers, double enjoyed, double learned) {

        return LearningDial.builder().enjoyed(enjoyed).learned(learned).enjoyedAnswers(answers).learnedAnswers(answers)
                .sessions(2).status(StatusRules.learning(learned, answers)).build();
    }

    /** A healthy baseline: six speakers sharing the stage, on rhythm, reused. Each test tweaks one axis. */
    private HealthSnapshot.HealthSnapshotBuilder healthy() {

        return HealthSnapshot.builder().window("90d").generatedAt(Instant.parse("2026-06-11T12:00:00Z"))
                .stage(stage(2L, 2L, 2L, 1L, 1L, 1L)).curation(new HealthSnapshot.CurationDial(6, 0.5))
                .cadence(cadence(CadenceStatus.STEADY, StatusCode.ON_RHYTHM))
                .people(new HealthSnapshot.PeopleDial(20, 12, FLAT))
                .engagement(new HealthSnapshot.EngagementDial(10, FLAT))
                .membershipGrowth(new HealthSnapshot.MembershipGrowthDial(3, 40, FLAT))
                .reach(new HealthSnapshot.CrossTeamReachDial(true, 4, 3, 2, 6, 6, FLAT)).reuse(reuse(5, 2));
    }

    private List<Finding.Code> codes(HealthSnapshot s) {

        return DiagnosisEngine.diagnose(s).findings().stream().map(Finding::code).toList();
    }

    @Test
    void diagnose_healthyCommunity() {

        // when
        var actual = codes(healthy().build());

        // then
        assertThat(actual).containsExactly(Finding.Code.WELL_DISTRIBUTED, Finding.Code.ON_RHYTHM,
                Finding.Code.KNOWLEDGE_REUSED);
    }

    @Test
    void singlePoint_needsThreeTalks() {

        // when / then
        assertThat(codes(healthy().stage(stage(2L)).build())).doesNotContain(Finding.Code.SINGLE_POINT);
        assertThat(codes(healthy().stage(stage(3L)).build())).contains(Finding.Code.SINGLE_POINT)
                .doesNotContain(Finding.Code.OVER_RELIANCE, Finding.Code.WELL_DISTRIBUTED);
    }

    @Test
    void overReliance_strictlyMoreThanHalf() {

        // when / then
        assertThat(codes(healthy().stage(stage(2L, 1L, 1L)).build())).doesNotContain(Finding.Code.OVER_RELIANCE,
                Finding.Code.SINGLE_POINT);
        assertThat(codes(healthy().stage(stage(3L, 1L, 1L)).build())).contains(Finding.Code.OVER_RELIANCE);
        assertThat(codes(healthy().stage(stage(1L, 1L)).build())).doesNotContain(Finding.Code.OVER_RELIANCE);
    }

    @Test
    void curationLeansOnOne_needsFiveActsAndSeventyFivePercent() {

        // when / then
        assertThat(codes(healthy().curation(new HealthSnapshot.CurationDial(4, 1.0)).build()))
                .doesNotContain(Finding.Code.CURATION_LEANS_ON_ONE);
        assertThat(codes(healthy().curation(new HealthSnapshot.CurationDial(5, 0.8)).build()))
                .contains(Finding.Code.CURATION_LEANS_ON_ONE);
        assertThat(codes(healthy().curation(new HealthSnapshot.CurationDial(8, 0.75)).build()))
                .contains(Finding.Code.CURATION_LEANS_ON_ONE);
        assertThat(codes(healthy().curation(new HealthSnapshot.CurationDial(10, 0.7)).build()))
                .doesNotContain(Finding.Code.CURATION_LEANS_ON_ONE);
    }

    @Test
    void narrowReach_speakersFromOneTeam() {

        // given
        var oneTeam = new HealthSnapshot.CrossTeamReachDial(true, 1, 1, 1, 6, 6, FLAT);

        // when / then
        assertThat(codes(healthy().reach(oneTeam).build())).contains(Finding.Code.NARROW_REACH);
        assertThat(codes(healthy().reach(oneTeam).stage(stage(4L)).build())).doesNotContain(Finding.Code.NARROW_REACH);
        assertThat(codes(healthy().reach(new HealthSnapshot.CrossTeamReachDial(false, 0, 0, 0, 0, 6, FLAT)).build()))
                .doesNotContain(Finding.Code.NARROW_REACH);
    }

    @Test
    void narrowReach_partialTeamData_doesNotFire() {

        // given
        var oneOfSixWithUnit = new HealthSnapshot.CrossTeamReachDial(true, 1, 1, 1, 1, 6, FLAT);

        // when / then
        assertThat(codes(healthy().reach(oneOfSixWithUnit).build())).doesNotContain(Finding.Code.NARROW_REACH);
    }

    @Test
    void narrowReach_fullTeamData_fires() {

        // given
        var allSixWithUnit = new HealthSnapshot.CrossTeamReachDial(true, 1, 1, 1, 6, 6, FLAT);

        // when / then
        assertThat(codes(healthy().reach(allSixWithUnit).build())).contains(Finding.Code.NARROW_REACH);
    }

    @Test
    void wellDistributed_threeForHalf() {

        // when / then
        assertThat(codes(healthy().stage(stage(1L, 1L, 1L, 1L)).build())).doesNotContain(Finding.Code.WELL_DISTRIBUTED);
        assertThat(codes(healthy().stage(stage(1L, 1L, 1L, 1L, 1L)).build())).contains(Finding.Code.WELL_DISTRIBUTED);
    }

    @Test
    void dormant() {

        // when
        var actual = codes(healthy().cadence(cadence(CadenceStatus.DORMANT, StatusCode.DORMANT)).build());

        // then
        assertThat(actual).contains(Finding.Code.DORMANT).doesNotContain(Finding.Code.ON_RHYTHM, Finding.Code.FADING);
    }

    @Test
    void fading_peopleDown() {

        // when / then
        assertThat(codes(healthy().cadence(cadence(CadenceStatus.SLOWING, StatusCode.SLOWING))
                .people(new HealthSnapshot.PeopleDial(5, 2, DOWN)).build())).contains(Finding.Code.FADING);
        assertThat(codes(healthy().cadence(cadence(CadenceStatus.STALLED, StatusCode.STALLED))
                .people(new HealthSnapshot.PeopleDial(5, 2, DOWN)).build())).contains(Finding.Code.FADING);
        assertThat(codes(healthy().cadence(cadence(CadenceStatus.SLOWING, StatusCode.SLOWING)).build()))
                .doesNotContain(Finding.Code.FADING);
        assertThat(codes(healthy().people(new HealthSnapshot.PeopleDial(5, 2, DOWN)).build()))
                .doesNotContain(Finding.Code.FADING);
    }

    @Test
    void startingUp_firesNoFading() {

        // when
        var actual = codes(healthy().cadence(cadence(CadenceStatus.STALLED, StatusCode.STARTING_UP))
                .people(new HealthSnapshot.PeopleDial(1, 1, DOWN)).build());

        // then
        assertThat(actual).doesNotContain(Finding.Code.FADING, Finding.Code.DORMANT);
    }

    @Test
    void socialClubDrift_noTalks() {

        // given
        var drifting = healthy().stage(stage()).engagement(new HealthSnapshot.EngagementDial(9, FLAT))
                .reuse(reuse(0, 0));

        // when / then
        assertThat(codes(drifting.build())).contains(Finding.Code.SOCIAL_CLUB_DRIFT);
        assertThat(codes(drifting.cadence(cadence(CadenceStatus.DORMANT, StatusCode.DORMANT)).build()))
                .doesNotContain(Finding.Code.SOCIAL_CLUB_DRIFT);
        assertThat(codes(healthy().stage(stage()).engagement(new HealthSnapshot.EngagementDial(9, FLAT)).build()))
                .doesNotContain(Finding.Code.SOCIAL_CLUB_DRIFT);
        assertThat(codes(healthy().stage(stage(1L)).engagement(new HealthSnapshot.EngagementDial(9, FLAT))
                .reuse(reuse(0, 0)).build())).doesNotContain(Finding.Code.SOCIAL_CLUB_DRIFT);
        assertThat(codes(healthy().stage(stage()).engagement(new HealthSnapshot.EngagementDial(0, FLAT))
                .reuse(reuse(0, 0)).build())).doesNotContain(Finding.Code.SOCIAL_CLUB_DRIFT);
    }

    @Test
    void interestWithoutPayoff_savedNoneHelped() {

        // when
        var actual = codes(healthy().reuse(reuse(18, 0)).build());

        // then
        assertThat(actual).contains(Finding.Code.INTEREST_WITHOUT_PAYOFF).doesNotContain(Finding.Code.KNOWLEDGE_REUSED);
    }

    @Test
    void diagnose_orderedRiskThenGapThenStrength() {

        // given one voice (risk), saves with no payoff (gap), steady (strength)
        var s = healthy().stage(stage(3L)).reuse(reuse(18, 0)).build();

        // when
        var actual = DiagnosisEngine.diagnose(s).findings().stream().map(Finding::kind).toList();

        // then
        assertThat(actual).containsExactly(Finding.Kind.RISK, Finding.Kind.GAP, Finding.Kind.STRENGTH);
    }

    @Test
    void learningLands_fromEightWithFiveAnswers() {

        // when / then
        assertThat(codes(healthy().learning(learning(5, 7.0, 8.0)).build())).containsExactly(
                Finding.Code.WELL_DISTRIBUTED, Finding.Code.ON_RHYTHM, Finding.Code.KNOWLEDGE_REUSED,
                Finding.Code.LEARNING_LANDS);
        assertThat(codes(healthy().learning(learning(5, 7.0, 7.9)).build()))
                .doesNotContain(Finding.Code.LEARNING_LANDS);
        assertThat(codes(healthy().learning(learning(4, 7.0, 9.0)).build()))
                .doesNotContain(Finding.Code.LEARNING_LANDS);
    }

    @Test
    void enjoyedNotLearned_enjoyedFromSevenAndAHalfLearnedBelowSix() {

        // when / then
        assertThat(codes(healthy().learning(learning(5, 7.5, 5.9)).build())).contains(Finding.Code.ENJOYED_NOT_LEARNED);
        assertThat(codes(healthy().learning(learning(5, 7.4, 5.9)).build()))
                .doesNotContain(Finding.Code.ENJOYED_NOT_LEARNED);
        assertThat(codes(healthy().learning(learning(5, 9.0, 6.0)).build()))
                .doesNotContain(Finding.Code.ENJOYED_NOT_LEARNED);
        assertThat(codes(healthy().learning(learning(4, 9.0, 3.0)).build()))
                .doesNotContain(Finding.Code.ENJOYED_NOT_LEARNED);
    }

    @Test
    void enjoyedNotLearned_needsFiveAnswersOfEachQuestion() {

        // given five enjoyed answers but only four learned ones
        var learning = LearningDial.builder().enjoyed(9.0).learned(3.0).enjoyedAnswers(5).learnedAnswers(4)
                .status(StatusRules.learning(3.0, 4)).build();

        // when
        var actual = codes(healthy().learning(learning).build());

        // then
        assertThat(actual).doesNotContain(Finding.Code.ENJOYED_NOT_LEARNED);
    }

    @Test
    void sessionsLandPoorly_enjoyedBelowFive() {

        // when / then
        assertThat(codes(healthy().learning(learning(5, 4.9, 7.0)).build()))
                .contains(Finding.Code.SESSIONS_LAND_POORLY);
        assertThat(codes(healthy().learning(learning(5, 5.0, 7.0)).build()))
                .doesNotContain(Finding.Code.SESSIONS_LAND_POORLY);
        assertThat(codes(healthy().learning(learning(4, 2.0, 7.0)).build()))
                .doesNotContain(Finding.Code.SESSIONS_LAND_POORLY);
    }

    @Test
    void learningGaps_followInterestWithoutPayoff() {

        // given saves with no payoff, and sessions enjoyed but neither teaching nor landing
        var both = LearningDial.builder().enjoyed(4.0).learned(2.0).enjoyedAnswers(5).learnedAnswers(5)
                .status(StatusRules.learning(2.0, 5)).build();
        var enjoyedNotLearned = learning(5, 8.0, 5.0);

        // when / then
        assertThat(codes(healthy().reuse(reuse(18, 0)).learning(enjoyedNotLearned).build())).containsExactly(
                Finding.Code.INTEREST_WITHOUT_PAYOFF, Finding.Code.ENJOYED_NOT_LEARNED, Finding.Code.WELL_DISTRIBUTED,
                Finding.Code.ON_RHYTHM);
        assertThat(codes(healthy().reuse(reuse(18, 0)).learning(both).build())).containsExactly(
                Finding.Code.INTEREST_WITHOUT_PAYOFF, Finding.Code.SESSIONS_LAND_POORLY, Finding.Code.WELL_DISTRIBUTED,
                Finding.Code.ON_RHYTHM);
    }
}
