package dev.axonia.onboarding.domain;

import java.time.Instant;

import lombok.Builder;
import lombok.Value;
import lombok.With;

/**
 * One frozen step as it is stored. {@code route} is the exact path a visit is matched against, and {@code detail} is
 * the live content the step pointed at when it was assembled, null for a step that names a place rather than a thing.
 */
@Value
@Builder(toBuilder = true)
public class OnboardingStepRecord {

    String stepKey;

    String route;

    String detail;

    @With
    Instant visitedAt;
}
