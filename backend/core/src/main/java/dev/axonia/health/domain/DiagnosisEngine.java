package dev.axonia.health.domain;

import java.util.ArrayList;
import java.util.List;

import dev.axonia.health.domain.Finding.Code;
import dev.axonia.health.domain.Finding.Kind;

/** Deterministic reading of a snapshot into ordered findings, risks first. Never sums anything into a score. */
public final class DiagnosisEngine {

    static final int CURATION_MIN_ACTS = 5;
    static final double CURATION_TOP_SHARE = 0.75;
    static final double ENJOYED_A_LOT = 7.5;
    static final double ENJOYED_NOT_LEARNED_LEARNED = 6.0;
    static final double SESSIONS_LAND_POORLY_ENJOYED = 5.0;

    private DiagnosisEngine() {
    }

    public static Diagnosis diagnose(HealthSnapshot s) {

        var findings = new ArrayList<Finding>();
        risks(s, findings);
        gaps(s, findings);
        strengths(s, findings);
        return new Diagnosis(findings);
    }

    private static void risks(HealthSnapshot s, List<Finding> out) {

        if (isSinglePoint(s.stage())) {
            out.add(new Finding(Kind.RISK, Code.SINGLE_POINT));
        }
        if (isOverReliance(s.stage())) {
            out.add(new Finding(Kind.RISK, Code.OVER_RELIANCE));
        }
        if (isCurationLeaningOnOne(s.curation())) {
            out.add(new Finding(Kind.RISK, Code.CURATION_LEANS_ON_ONE));
        }
        if (s.cadence().rhythm() == CadenceStatus.DORMANT) {
            out.add(new Finding(Kind.RISK, Code.DORMANT));
        }
        if (isFading(s)) {
            out.add(new Finding(Kind.RISK, Code.FADING));
        }
    }

    private static boolean isSinglePoint(HealthSnapshot.StageDial stage) {

        return stage.talks() >= StatusRules.MIN_TALKS && stage.speakers() == 1;
    }

    private static boolean isOverReliance(HealthSnapshot.StageDial stage) {

        var topShare = stage.distribution().isEmpty() ? 0.0 : stage.distribution().getFirst();
        return stage.talks() >= StatusRules.MIN_TALKS && stage.speakers() > 1 && StatusRules.leansOnOne(topShare);
    }

    private static boolean isCurationLeaningOnOne(HealthSnapshot.CurationDial curation) {

        return curation.acts() >= CURATION_MIN_ACTS && curation.topShare() >= CURATION_TOP_SHARE - 1e-9;
    }

    private static boolean isFading(HealthSnapshot s) {

        var rhythm = s.cadence().rhythm();
        var slowing = rhythm == CadenceStatus.SLOWING || rhythm == CadenceStatus.STALLED;
        var startingUp = s.cadence().status().code() == StatusCode.STARTING_UP;
        return slowing && !startingUp && s.people().trend().direction() == Trend.Direction.DOWN;
    }

    private static void gaps(HealthSnapshot s, List<Finding> out) {

        if (isSocialClubDrift(s)) {
            out.add(new Finding(Kind.GAP, Code.SOCIAL_CLUB_DRIFT));
        }
        if (s.reuse().saves() > 0 && s.reuse().helped() == 0) {
            out.add(new Finding(Kind.GAP, Code.INTEREST_WITHOUT_PAYOFF));
        }
        if (isEnjoyedNotLearned(s.learning())) {
            out.add(new Finding(Kind.GAP, Code.ENJOYED_NOT_LEARNED));
        }
        if (enjoyedIsRead(s.learning()) && s.learning().enjoyed() < SESSIONS_LAND_POORLY_ENJOYED) {
            out.add(new Finding(Kind.GAP, Code.SESSIONS_LAND_POORLY));
        }
        if (isNarrowReach(s)) {
            out.add(new Finding(Kind.GAP, Code.NARROW_REACH));
        }
    }

    private static boolean isSocialClubDrift(HealthSnapshot s) {

        return s.engagement().loves() > 0 && s.stage().talks() == 0 && s.reuse().helped() == 0
                && s.cadence().rhythm() != CadenceStatus.DORMANT;
    }

    private static boolean isEnjoyedNotLearned(LearningDial learning) {

        return enjoyedIsRead(learning) && learnedIsRead(learning) && learning.enjoyed() >= ENJOYED_A_LOT
                && learning.learned() < ENJOYED_NOT_LEARNED_LEARNED;
    }

    private static boolean enjoyedIsRead(LearningDial learning) {

        return learning.enjoyedAnswers() >= StatusRules.MIN_FEEDBACK_ANSWERS;
    }

    private static boolean learnedIsRead(LearningDial learning) {

        return learning.learnedAnswers() >= StatusRules.MIN_FEEDBACK_ANSWERS;
    }

    private static boolean isNarrowReach(HealthSnapshot s) {

        var reach = s.reach();
        var speakers = s.stage().speakers();
        return reach.available() && reach.teams() == 1 && speakers > 1 && reach.speakersWithUnit() == speakers;
    }

    private static void strengths(HealthSnapshot s, List<Finding> out) {

        if (s.stage().speakersForHalf() >= StatusRules.SHARED_STAGE_SPEAKERS) {
            out.add(new Finding(Kind.STRENGTH, Code.WELL_DISTRIBUTED));
        }
        if (s.cadence().rhythm() == CadenceStatus.STEADY) {
            out.add(new Finding(Kind.STRENGTH, Code.ON_RHYTHM));
        }
        if (s.reuse().helped() > 0) {
            out.add(new Finding(Kind.STRENGTH, Code.KNOWLEDGE_REUSED));
        }
        if (learnedIsRead(s.learning()) && s.learning().learned() >= StatusRules.LEARNING_A_LOT) {
            out.add(new Finding(Kind.STRENGTH, Code.LEARNING_LANDS));
        }
    }
}
