package dev.axonia.health.domain;

/**
 * One plain-language reading of the panel. The backend decides which findings fire (rule logic); the frontend phrases
 * them from the code plus the dials it already holds. Findings are never summed into a score.
 */
public record Finding(Kind kind, Code code) {

    public enum Kind {
        STRENGTH, RISK, GAP
    }

    public enum Code {
        SINGLE_POINT, OVER_RELIANCE, FADING, DORMANT, SOCIAL_CLUB_DRIFT, INTEREST_WITHOUT_PAYOFF, NARROW_REACH, WELL_DISTRIBUTED, ON_RHYTHM, KNOWLEDGE_REUSED
    }
}
