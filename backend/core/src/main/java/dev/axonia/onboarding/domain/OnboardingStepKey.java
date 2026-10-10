package dev.axonia.onboarding.domain;

/**
 * Every step either path can hold. The key is domain: it decides what the step IS and what order it comes in. Where it
 * points and what it is called are presentation, and belong to the frontend.
 */
public enum OnboardingStepKey {

    LOOK_AND_FEEL, FIRST_SESSION, BACKLOG, FIRST_PEOPLE, LIBRARY, HEALTH,

    MOST_LOVED_ARTIFACT, LAST_HELD_SESSION, NEXT_SESSION, TOP_TOPIC, DECLARE_EXPERTISE
}
