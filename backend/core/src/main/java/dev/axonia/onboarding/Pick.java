package dev.axonia.onboarding;

/**
 * One thing a member step can be pointed at, in the only two parts onboarding needs: the id that builds the route, and
 * the label the step shows beneath its name. Deliberately shared by all three ports - an artifact, a session and a
 * topic are different aggregates, but a frozen step treats them identically because it keeps a copy rather than a
 * reference.
 */
public record Pick(Long id, String label) {
}
