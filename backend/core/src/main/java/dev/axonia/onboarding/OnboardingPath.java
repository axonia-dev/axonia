package dev.axonia.onboarding;

import java.util.List;

import lombok.Builder;

/** An open path: which guide, which community, and its steps in the order the planner froze them. */
@Builder
public record OnboardingPath(OnboardingVariant variant, String communitySlug, List<OnboardingStep> steps) {
}
