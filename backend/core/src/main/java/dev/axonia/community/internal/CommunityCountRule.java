package dev.axonia.community.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.PolicyOpinions;
import dev.axonia.community.CommunityCountPolicy;
import lombok.RequiredArgsConstructor;

/** Asks the contributed {@link CommunityCountPolicy}s, and falls back to the Enterprise answer: not fixed. */
@Component
@RequiredArgsConstructor
public class CommunityCountRule {

    private final List<CommunityCountPolicy> policies;

    public boolean countFixedAtFounding(String organizationId) {

        return PolicyOpinions.sole(policies, policy -> policy.countFixedAtFounding(organizationId)).orElse(false);
    }
}
