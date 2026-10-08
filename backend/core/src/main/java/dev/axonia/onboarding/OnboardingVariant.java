package dev.axonia.onboarding;

/**
 * Which of the two product-authored paths a person was given. Chosen once, at first arrival at a community they belong
 * to, and never revisited: a member later promoted to organizer, or one who later creates their own hub, gets no second
 * path.
 */
public enum OnboardingVariant {

    /** For somebody standing in front of a community that is empty and has nobody in it. */
    ORGANIZER,

    /** For somebody who just joined a community that has been running. */
    MEMBER
}
