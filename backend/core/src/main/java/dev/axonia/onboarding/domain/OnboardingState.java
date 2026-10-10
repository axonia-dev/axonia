package dev.axonia.onboarding.domain;

/**
 * Whether this person has ever been given a path, is partway through one, or is done with theirs.
 *
 * <p>
 * {@code COMPLETED} and {@code CLOSED} are both over, and both render nothing: they are told apart so a frontend that
 * watches a path go from {@code OPEN} to {@code COMPLETED} in one session can mark the finish, and never mistakes a
 * dismissal one step from the end for one.
 */
public enum OnboardingState {

    NEVER_ASSIGNED, OPEN, COMPLETED, CLOSED
}
