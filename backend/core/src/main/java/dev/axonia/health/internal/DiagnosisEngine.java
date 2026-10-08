package dev.axonia.health.internal;

import java.util.ArrayList;
import java.util.List;

import dev.axonia.health.CadenceStatus;
import dev.axonia.health.Diagnosis;
import dev.axonia.health.Finding;
import dev.axonia.health.Finding.Code;
import dev.axonia.health.Finding.Kind;
import dev.axonia.health.HealthSnapshot;
import dev.axonia.health.Trend;

/** Deterministic reading of a snapshot into ordered findings. Never sums anything into a score. */
public final class DiagnosisEngine {

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

        if (s.busFactor() == 1) {
            out.add(new Finding(Kind.RISK, Code.SINGLE_POINT));
        }
        if (s.busFactor() == 2) {
            out.add(new Finding(Kind.RISK, Code.OVER_RELIANCE));
        }
        var slowing = s.cadence().status() == CadenceStatus.SLOWING || s.cadence().status() == CadenceStatus.STALLED;
        if (slowing && s.participation().trend().direction() == Trend.Direction.DOWN) {
            out.add(new Finding(Kind.RISK, Code.FADING));
        }
        if (s.cadence().status() == CadenceStatus.DORMANT) {
            out.add(new Finding(Kind.RISK, Code.DORMANT));
        }
    }

    private static void gaps(HealthSnapshot s, List<Finding> out) {

        if (isSocialClubDrift(s)) {
            out.add(new Finding(Kind.GAP, Code.SOCIAL_CLUB_DRIFT));
        }
        if (isInterestWithoutPayoff(s)) {
            out.add(new Finding(Kind.GAP, Code.INTEREST_WITHOUT_PAYOFF));
        }
        if (isNarrowReach(s)) {
            out.add(new Finding(Kind.GAP, Code.NARROW_REACH));
        }
    }

    private static boolean isSocialClubDrift(HealthSnapshot s) {
        return s.engagement().loves() > 0 && s.busFactor() == 0 && s.reuse().helped() == 0
                && s.cadence().status() != CadenceStatus.DORMANT;
    }

    private static boolean isInterestWithoutPayoff(HealthSnapshot s) {
        return s.reuse().saves() > 0 && s.reuse().helped() == 0;
    }

    private static boolean isNarrowReach(HealthSnapshot s) {
        return s.reach().available() && s.reach().teams() == 1 && s.reach().totalContributors() > 1;
    }

    private static void strengths(HealthSnapshot s, List<Finding> out) {

        if (s.busFactor() >= 5) {
            out.add(new Finding(Kind.STRENGTH, Code.WELL_DISTRIBUTED));
        }
        if (s.cadence().status() == CadenceStatus.STEADY) {
            out.add(new Finding(Kind.STRENGTH, Code.ON_RHYTHM));
        }
        if (s.reuse().helped() > 0) {
            out.add(new Finding(Kind.STRENGTH, Code.KNOWLEDGE_REUSED));
        }
    }
}
