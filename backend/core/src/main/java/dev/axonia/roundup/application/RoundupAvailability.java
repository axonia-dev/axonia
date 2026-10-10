package dev.axonia.roundup.application;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.roundup.api.spi.RoundupAvailabilityPolicy;
import dev.axonia.shared.api.policy.PolicyOpinions;
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
