package dev.axonia.onboarding;

import lombok.Builder;

/**
 * What {@code GET /me/onboarding} answers. {@code path} is null unless {@code state} is {@link OnboardingState#OPEN}:
 * the frontend has to tell "never assigned" from "closed" apart, because only the first of those may be assigned.
 */
@Builder
public record OnboardingView(OnboardingState state, OnboardingPath path) {
}
