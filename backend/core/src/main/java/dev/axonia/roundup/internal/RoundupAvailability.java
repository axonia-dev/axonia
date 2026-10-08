package dev.axonia.roundup.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.PolicyOpinions;
import dev.axonia.roundup.RoundupAvailabilityPolicy;
import lombok.RequiredArgsConstructor;

/** Asks the contributed {@link RoundupAvailabilityPolicy}s, and falls back to the Enterprise answer: available. */
@Component
@RequiredArgsConstructor
public class RoundupAvailability {

    private final List<RoundupAvailabilityPolicy> policies;

    public boolean in(String organizationId) {

        return PolicyOpinions.sole(policies, policy -> policy.available(organizationId)).orElse(true);
    }
}
