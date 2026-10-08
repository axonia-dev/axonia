package dev.axonia.onboarding;

import lombok.Builder;

/** One step as a surface sees it. {@code detail} is null for a step that names a place rather than a thing. */
@Builder
public record OnboardingStep(OnboardingStepKey key, String route, String detail, boolean visited) {
}
